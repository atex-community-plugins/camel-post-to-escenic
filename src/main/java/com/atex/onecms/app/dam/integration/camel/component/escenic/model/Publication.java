package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;

/**
 *
 * @author jakub
 */
public class Publication {

	private String title;
	private String href;
	private List<Link> link;

	@XmlAttribute(name = "href")
	public String getHref() {
		return href;
	}

	@XmlAttribute(name = "title")
	public String getTitle() {
		return title;
	}

	public void setHref(String href) {
		this.href = href;
	}

	public void setTitle(String title) {
		this.title = title;
	}
	@XmlElement(namespace="http://www.w3.org/2005/Atom", name="link")
	public List<Link> getLink() {
		return link;
	}

	public void setLink(List<Link> link) {
		this.link = link;
	}

}
