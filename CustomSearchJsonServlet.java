package com.citrixosd.service.search;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.jackrabbit.util.Text;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.siteworx.rewrite.transformer.ContextRootTransformer;

/**
 * Sling Servlet to get search result in JSON format
 * @author sugupta
 */

@SlingServlet(paths = "/bin/citrix/getSearchJSON", methods = "GET")
@Properties({ @Property(name = "service.description", value = "This sling servlet provides result in JSON formate when query string: 'q' and path:'dir' are provided") })
public class CustomSearchJsonServlet extends SlingAllMethodsServlet {
	
	private static final long serialVersionUID = -6975727597454951820L;
	private static Logger LOGGER = LoggerFactory.getLogger(CustomSearchJsonServlet.class);
	private static final String QUERY_PARAM_NAME = "q";
	private static final String SEARCH_DIR = "dir";

	@Reference
	protected QueryBuilder queryBuilder;

	@Reference
	protected ContextRootTransformer contextRootTransformer;

	protected void doGet(SlingHttpServletRequest request,SlingHttpServletResponse response) throws ServletException,IOException {
		handleQuery(request, response);
	}

	protected void doPost(SlingHttpServletRequest request,SlingHttpServletResponse response) throws ServletException,IOException {
		handleQuery(request, response);
	}

	/**
	 * @param request
	 * @return Map
	 */
	private Map<String, String> setSerachProperty(SlingHttpServletRequest request) {
		final String fulltextSearchTerm = StringEscapeUtils.escapeJavaScript(request.getParameter(QUERY_PARAM_NAME));
		// prevent wild card search
		if (fulltextSearchTerm == null || fulltextSearchTerm.trim().isEmpty() || fulltextSearchTerm.trim().startsWith("*") || fulltextSearchTerm.trim().endsWith("*")) {
			LOGGER.debug("returning no result as blank query or wildcard present in query string:"+ fulltextSearchTerm);
			return null;
		}
		final String serchdir = request.getParameter(SEARCH_DIR) != null ? request
				.getParameter(SEARCH_DIR).trim() : null;
		LOGGER.debug("Executing Serach Query under directory :" + serchdir);

		// create query description as hash map (simplest way, same as form
		// post)
		Map<String, String> map = new HashMap<String, String>();
		map.put("path", serchdir);
		map.put("type", "cq:Page");
		map.put("group.p.or", "true");
		map.put("group.1_property", "fn:lower-case(@jcr:content/jcr:title)");
		map.put("group.1_property.value", fulltextSearchTerm + "%");
		map.put("group.1_property.operation", "like");
		map.put("group.2_property","fn:lower-case(@jcr:content/jcr:description)");
		map.put("group.2_property.value", fulltextSearchTerm + "%");
		map.put("group.2_property.operation", "like");
		map.put("p.offset", "0");
		map.put("orderby", "@jcr:content/cq:lastModified");
		map.put("orderby.sort", "desc");
		map.put("orderby.index", "true");
		return map;
	}

	/**
	 * @param request
	 * @param response
	 * @throws IOException
	 * @throws ServletException
	 */
	private void handleQuery(SlingHttpServletRequest request,SlingHttpServletResponse response) throws IOException,ServletException {
		response.setContentType("application/json");
		response.setCharacterEncoding("utf-8");
		if (setSerachProperty(request) == null) {
			this.writeError(response);
			return;
		}
		Session session = (Session) request.getResourceResolver().adaptTo(
				Session.class);
		Query query = this.queryBuilder.createQuery(
				PredicateGroup.create(setSerachProperty(request)), session);
		SearchResult result = query.getResult();
		LOGGER.debug("Query Executed :" + result.getQueryStatement());
		LOGGER.debug("Total maches found for query  :"+ result.getTotalMatches());
		LOGGER.debug("Time(ms) taken by serch Query :"+ result.getExecutionTimeMillis());
		try {
			Writer out = response.getWriter();
			JSONWriter writer = new JSONWriter(out);
			writer.object();
			// write Meta Data of search result
			writer.key("success").value(true);
			writer.key("results").value(result.getHits().size());
			writer.key("total").value(result.getTotalMatches());
			writer.key("offset").value(result.getStartIndex());
			writer.key("hits").array();
			// write search Content
			writeHits(result, writer);
			writer.endArray();
			writer.endObject();

		} catch (JSONException e) {

			LOGGER.info("There is some problem while writing serach result JSON :"+ e.getStackTrace());
		} catch (RepositoryException e) {
			LOGGER.info("There is some repository exception while runing serach query :"+ e.getStackTrace());
		}
	}

	/**
	 * @param path
	 * @return sitePath
	 */
	private String toSiteURL(final String path) {
		String siteURL = null;
		if (path != null && !path.trim().isEmpty()) {

			try {
				siteURL = contextRootTransformer.transform(path);
				return siteURL != null ? siteURL : path;
			} catch (RepositoryException e) {
				LOGGER.debug("There is some problem while transorming URL :"+ e.getStackTrace());
				return path;
			}
		}

		return path;
	}

	/**
	 * @param response
	 */
	private void writeError(SlingHttpServletResponse response) {

		try {
			Writer out = response.getWriter();
			JSONWriter writer = new JSONWriter(out);
			writer.object();
			writer.key("success").value(false);
			writer.key("error").value("No Results");
			writer.endObject();
		} catch (Exception e) {
			LOGGER.debug("There is some problem while writing serach result json :"+ e.getStackTrace());
		}

	}

	/**
	 * @param hit
	 * @param writer
	 * @throws RepositoryException
	 * @throws JSONException
	 */
	public void writeSimpleJson(Hit hit, JSONWriter writer) throws RepositoryException,JSONException {
		ValueMap properties = hit.getProperties();
		writer.key("path").value(toSiteURL(hit.getPath()));
		String excerpt = hit.getExcerpt();
		if (excerpt != null) {
			writer.key("excerpt").value(excerpt);
		}
		String name = Text.getName(hit.getPath());
		writer.key("name").value(name);
		writer.key("title").value(properties.get("jcr:title", name));
	}

	/**
	 * @param result
	 * @param writer
	 * @throws JSONException
	 * @throws RepositoryException
	 */
	private void writeHits(SearchResult result, JSONWriter writer) throws JSONException,RepositoryException {
		for (Hit hit : result.getHits()) {
			writer.object();
			writeSimpleJson(hit, writer);
			writer.endObject();
		}
	}
}
