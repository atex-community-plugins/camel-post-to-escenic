package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import com.atex.onecms.app.dam.integration.camel.component.escenic.ValueHandler;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 *
 * @author jakub
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Value {

	private List<Object> value;

	public Value () {
	}

	@XmlAnyElement(value=ValueHandler.class)
	@XmlMixed
	public List<Object> getValue() {
		return value;
	}

	public Value(List<Object> value) {
		this.value = value;
	}

	public void setValue(List<Object> value) {
		this.value = value;
	}

}
