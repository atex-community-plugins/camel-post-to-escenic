package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.*;

/**
 *
 * @author jakub
 */

public class Query {

	private String role;
	private String title;
	private String totalResults;
	private String searchTerms;
	private String related;
	private String start;
	private String end;

	public Query() {
	}

	@XmlAttribute(name = "start", namespace = "http://a9.com/-/opensearch/extensions/time/1.0/")
	public String getStart() {
		return start;
	}

	public void setStart(String start) {
		this.start = start;
	}

	@XmlAttribute(name = "end", namespace = "http://a9.com/-/opensearch/extensions/time/1.0/")
	public String getEnd() {
		return end;
	}

	public void setEnd(String end) {
		this.end = end;
	}

	@XmlAttribute(name = "related", namespace = "http://a9.com/-/opensearch/extensions/semantic/1.0/")
	public String getRelated() {
		return related;
	}

	public void setRelated(String related) {
		this.related = related;
	}

	@XmlAttribute
	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}
	@XmlAttribute
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}
	@XmlAttribute
	public String getTotalResults() {
		return totalResults;
	}

	public void setTotalResults(String totalResults) {
		this.totalResults = totalResults;
	}
	@XmlAttribute
	public String getSearchTerms() {
		return searchTerms;
	}

	public void setSearchTerms(String searchTerms) {
		this.searchTerms = searchTerms;
	}
}
