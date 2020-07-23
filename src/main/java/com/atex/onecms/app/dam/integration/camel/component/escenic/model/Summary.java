package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

/**
 *
 * @author jakub
 */
public class Summary {

	private String type;
	private String summary;

	public Summary(){
	}

	@XmlAttribute
	public String getType() {
		return type;
	}

	@XmlValue
	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public void setType(String type) {
		this.type = type;
	}
}
