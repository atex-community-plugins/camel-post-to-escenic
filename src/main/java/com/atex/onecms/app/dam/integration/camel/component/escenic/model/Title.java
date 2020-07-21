package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlValue;

/**
 *
 * @author jakub
 */
public class Title {

	private String type;
	private String title;

	public Title(){
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setType(String type) {
		this.type = type;
	}

	@XmlAttribute
	public String getType() {
		return type;
	}

	@XmlValue
	public String getTitle() {
		return title;
	}
}
