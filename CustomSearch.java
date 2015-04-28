package com.citrixosd.service.search;

import org.apache.sling.api.SlingHttpServletRequest;
import com.day.cq.search.result.SearchResult;

/**
 * CustomSearch Service will read request parameter and search repository content and provide search result  
 * 
 * @author sugupta
 * 
 */

public interface CustomSearch {
	
	/**
	 * @param request
	 * @return SearchResult
	 */
	public SearchResult search(SlingHttpServletRequest request);
	
	/**
	 * @param request
	 * @return HTMLString
	 */
	public String getSearchResultHTML(SlingHttpServletRequest request);
	
}
