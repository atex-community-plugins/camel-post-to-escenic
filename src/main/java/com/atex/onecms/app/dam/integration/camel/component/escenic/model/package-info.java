@XmlSchema(
	namespace = "http://www.w3.org/2005/Atom",
	elementFormDefault = XmlNsForm.QUALIFIED,
	xmlns = { @XmlNs(prefix = "", namespaceURI = "http://www.w3.org/2005/Atom"),
		@XmlNs(prefix = "vdf", namespaceURI = "http://www.vizrt.com/types"),
		@XmlNs(prefix = "app", namespaceURI = "http://www.w3.org/2007/app"),
		@XmlNs(prefix = "metadata", namespaceURI = "http://xmlns.escenic.com/2010/atom-metadata"),
		@XmlNs(prefix = "dcterms", namespaceURI = "http://purl.org/dc/terms/"),
		@XmlNs(prefix = "vaext", namespaceURI = "http://www.vizrt.com/atom-ext"),
		@XmlNs(prefix = "age", namespaceURI = "http://purl.org/atompub/age/1.0")
	}
)

package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;



