package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

///**
// * @author peterabjohns
// */
//@XmlAccessorType(XmlAccessType.FIELD)
//@XmlType(name = "FileType", propOrder = {
//	"encoding","name"
//})

public class FileType {

//	@XmlAttribute
	protected String encoding;

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEncodedFile() {
		return encodedFile;
	}

	public void setEncodedFile(String encodedFile) {
		this.encodedFile = encodedFile;
	}

	//	@XmlAttribute
	protected String name;

//	@XmlValue
	protected String encodedFile;



}
