package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.XmlElement;

/**
 *
 * @author jakub
 */
public class List {

	private Payload payload;

	@XmlElement(namespace="http://www.vizrt.com/types")
	public Payload getPayload() {
		return payload;
	}

	public void setPayload(Payload payload) {
		this.payload = payload;
	}

}
