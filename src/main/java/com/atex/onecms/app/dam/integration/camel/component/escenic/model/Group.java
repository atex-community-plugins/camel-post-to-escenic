package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 *
 * @author jakub
 */

public class Group {

	private String title;

	private List<Query> query;

	public Group() {
	}

	@XmlElement(name = "Query", namespace="http://a9.com/-/spec/opensearch/1.1/")
	public List<Query> getQuery() {
		return query;
	}

	public void setQuery(List<Query> query) {
		this.query = query;
	}

	@XmlAttribute
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}
}
