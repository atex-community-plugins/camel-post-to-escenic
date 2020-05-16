package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 *
 * @author jakub
 */
public class Payload {

	private List<Field> field;
	private String model;

	public Payload() {
	}

	public Payload(List<Field> field) {
		this.field = field;
	}

	@XmlElement(name="field", namespace="http://www.vizrt.com/types", type=Field.class)
	public List<Field> getField() {
		return field;
	}

	@XmlAttribute
	public String getModel() {
		return model;
	}

	public void setField(List<Field> field) {
		this.field = field;
	}

	public void setModel(String model) {
		this.model = model;
	}
}
