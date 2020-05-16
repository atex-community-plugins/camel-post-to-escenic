package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 *
 * @author jakub
 */
@XmlType(propOrder={"draft","state"})
public class Control {

	private java.util.List<State> state;

	private String draft;

	public Control() {
	}

	@XmlElement(name = "state", namespace="http://www.vizrt.com/atom-ext")
	public java.util.List<State> getState() {
		return state;
	}

	public void setState(java.util.List<State> state) {
		this.state = state;
	}

	@XmlElement(name="draft", namespace = "http://www.w3.org/2007/app")
	public String getDraft() {
		return draft;
	}

	public void setDraft(String draft) {
		this.draft = draft;
	}
}
