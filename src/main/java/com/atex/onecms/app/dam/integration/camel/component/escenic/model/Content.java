package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 *
 * @author jakub
 */
public class Content {

	private String type;
	private Payload payload;

	public Content() {
	}

	@XmlAttribute
	public String getType() {
		return type;
	}

	@XmlElement(namespace="http://www.vizrt.com/types")
	public Payload getPayload() {
		return payload;
	}

	public void setPayload(Payload payload) {
		this.payload = payload;
	}

	public void setType(String type) {
		this.type = type;
	}

}
