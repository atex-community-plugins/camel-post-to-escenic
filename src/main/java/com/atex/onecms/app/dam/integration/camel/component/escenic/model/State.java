package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

/**
 *
 * @author jakub
 */
public class State {

	private String name;
	private String href;
	private String state;

	public State(){
	}

	@XmlAttribute(name = "name")
	public String getName() {
		return name;
	}

	@XmlValue
	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public void setName(String name) {
		this.name = name;
	}

	@XmlAttribute(name = "href")
	public String getHref() {
		return href;
	}

	public void setHref(String href) {
		this.href = href;
	}
}
