package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.XmlElement;

/**
 *
 * @author jakub
 */
public class List {

	private Payload payload;

	private java.util.List<Payload> payloadList;

	public void setPayloadList(java.util.List<Payload> payloadList) { this.payloadList = payloadList; }

	@XmlElement(name="payload", namespace="http://www.vizrt.com/types", type=Payload.class)
	public java.util.List<Payload> getPayloadList() {
		return payloadList;
	}

	@XmlElement(namespace="http://www.vizrt.com/types")
	public Payload getPayload() {
		return payload;
	}

	public void setPayload(Payload payload) {
		this.payload = payload;
	}

}
