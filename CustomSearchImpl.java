package com.citrixosd.service.search;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.citrixosd.utils.ContextRootTransformUtil;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;

/**
 * One implementation of the {@link CustomSearch}.
 * @author sugupta,vgupta
 */

@Component(metatype = true, immediate = true)
@Service(value = CustomSearch.class)

public class CustomSearchImpl implements CustomSearch {
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = LoggerFactory.getLogger(CustomSearchImpl.class);
	
	private static final String QUERY_PARAM_NAME = "q";
	private static final String SEARCH_DIR = "dir";
	private static final String OFFSET = "offset";
	
	private String query;
	
	@Reference
	QueryBuilder qbuilder;
	
	@Override
	public SearchResult search(SlingHttpServletRequest request) {
		// TODO Auto-generated method stub
		SearchResult result = executeSearchQuery(request);
		LOGGER.info("Executing Serach Query for: " + this.getQuery());
		return result;
	}

	@Override
	public String getSearchResultHTML(SlingHttpServletRequest request) {
		// execute query
		SearchResult result = executeSearchQuery(request); 
		LOGGER.info("Executing Serach Query for :" + this.getQuery());		
		return writeHTMLSerachResult(request,result);
	}
	
	private SearchResult executeSearchQuery(SlingHttpServletRequest request) {
		SearchResult result = null;
		Map <String, String> map = setSerachProperty(request);
		if(map != null && !map.isEmpty()){
			Session session = request.getResource().getResourceResolver().adaptTo(Session.class);
			Query query = qbuilder.createQuery(PredicateGroup.create(map), session);
			result = query.getResult();
			LOGGER.info("Query Executed :" + result.getQueryStatement());
			LOGGER.info("Total maches found for query  :" + result.getTotalMatches());
			LOGGER.info("Time(ms) taken by serch Query :" + result.getExecutionTimeMillis());
		}
		return result;
	}

	private Map <String,String> setSerachProperty(SlingHttpServletRequest request) {
		final String fulltextSearchTerm = StringEscapeUtils.escapeJavaScript(request.getParameter(QUERY_PARAM_NAME));
		final String offset = request.getParameter(OFFSET);
		final String serchdir = request.getParameter(SEARCH_DIR).trim();
		
		//prevent wild card search 
		if(fulltextSearchTerm == null || fulltextSearchTerm.trim().isEmpty() || fulltextSearchTerm.trim().startsWith("*") || fulltextSearchTerm.trim().endsWith("*")) {
			return null;
		}
		
		this.setQuery(fulltextSearchTerm);
		
		LOGGER.info("Executing Serach Query under directory :" + serchdir);

		//create query description as hash map (simplest way, same as form post)
		Map <String,String> map = new HashMap <String,String>();
		map.put("path",serchdir);
		map.put("type","cq:Page");
		map.put("group.p.or","true");
		map.put("group.1_property","fn:lower-case(@jcr:content/jcr:title)");
		map.put("group.1_property.value",this.query.toLowerCase()+"%");
		map.put("group.1_property.operation","like");
		map.put("group.2_fulltext",this.query);
		map.put("group.2_fulltext.relPath","jcr:content");

		// can be done in map or with Query methods
		map.put("p.offset",offset); // same as query.setStart(0) below
		map.put("orderby","@jcr:content/cq:lastModified");
		map.put("orderby.sort","desc");
		
		return map;
	}
	
	private String writeHTMLSerachResult(SlingHttpServletRequest request, SearchResult result) {
		long totalMatches = result.getTotalMatches();
		long executionTime = result.getExecutionTimeMillis() / 1000;
		String timelap = executionTime > 0 ? "" + executionTime + " seconds" : "" + result.getExecutionTimeMillis() + " ms";
		if (result.getTotalMatches() > 0) {
			StringBuffer sb = new StringBuffer("<p> About " + totalMatches + "results (" + timelap + ") </P><br><div style='margin: 20px' class='serachResult'><ul>");
			for (Hit hit: result.getHits()) {
				try {
					String path = hit.getPath();
					String pageJCRTitle = hit.getTitle(); 
					ValueMap props = hit.getProperties(); //properties of the jcr:content subnode (if present) or the properties of this node itself.
					String pageTitle = props.get("pageTitle") != null ? props.get("pageTitle").toString() : "";
					String siteURLforPage = ContextRootTransformUtil.transformedPath(path, request);
					String linkOfpage = "<li><div class='serch-link'><a href='" + siteURLforPage + "' >" + pageJCRTitle + " </a></div><div class='link-teaser'>" + pageTitle + "</div></li>";
					sb.append(linkOfpage);
				} catch (RepositoryException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			sb.append("</ul></div>");
			return sb.toString();
			
		} else {
			return "";
		}
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}
}
