package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import org.apache.commons.lang.StringUtils;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 *
 * @author jakub
 */
@XmlRootElement
@XmlSeeAlso({Value.class})
@XmlAccessorType(XmlAccessType.FIELD)
public class Field {

	@XmlAttribute
	private String name;

	//	@XmlElementRef(type=Value.class, name="value")
	@XmlElement(name="value" , nillable=false, namespace="http://www.vizrt.com/types", type=Value.class)
	private Value value;

	@XmlElement(name="field", namespace="http://www.vizrt.com/types", type=Field.class)
	private List<Field> field;

	@XmlElement(name="list", namespace="http://www.vizrt.com/types", type=com.atex.onecms.app.dam.integration.camel.component.escenic.model.List.class)
	private com.atex.onecms.app.dam.integration.camel.component.escenic.model.List list;


//	@XmlValue(name="value" ,namespace="http://www.vizrt.com/types", type=Value.class)
//	@XmlElement(name="value" ,namespace="http://www.vizrt.com/types", type=Value.class)

//	@XmlElementRef(name = "div", type = Div.class)


	public Field() {
	}

	public Field(String name, Value value, List<Field> field, com.atex.onecms.app.dam.integration.camel.component.escenic.model.List list) {
		this.name = name;
		this.value = value;
		this.list = list;
		this.field = field;
	}

	public List<Field> getField() {
		return field;
	}

	public void setField(List<Field> field) {
		this.field = field;
	}




	public com.atex.onecms.app.dam.integration.camel.component.escenic.model.List getList() {
		return list;
	}

	public void setList(com.atex.onecms.app.dam.integration.camel.component.escenic.model.List list) {
		this.list = list;
	}

	public String getName() {
		return name;
	}

	public Value getValue() {
		return value;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setValue(Value value) {
		this.value = value;
	}

	public boolean fieldNameEqualsIgnoreCase(Field otherField) {
		return StringUtils.equalsIgnoreCase(this.getName(), otherField.getName());
	}
}
