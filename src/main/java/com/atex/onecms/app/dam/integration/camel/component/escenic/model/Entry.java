package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 *
 * @author jakub
 */
@XmlRootElement(name="entry")
public class Entry {
	private Title title;
	private String id;
	private List<Link> link;
	private Content content;
	private String uri;
	private String identifier;
	private Publication publication;
	private Control control;
	private String published;
	private String expires;
	private Summary summary;
	private String available;

	public Entry() {
	}

	@XmlElement(name = "expires", namespace="http://purl.org/atompub/age/1.0")
	public String getExpires() {
		return expires;
	}

	public void setExpires(String expires) {
		this.expires = expires;
	}

	@XmlElement(name = "available", namespace="http://purl.org/dc/terms/")
	public String getAvailable() {
		return available;
	}

	public void setAvailable(String available) {
		this.available = available;
	}

	@XmlElement(name = "uri")
	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	@XmlElement(name = "published")
	public String getPublished() {
		return published;
	}

	public void setPublished(String published) {
		this.published = published;
	}

	@XmlElement(name = "publication", namespace="http://xmlns.escenic.com/2010/atom-metadata")
	public Publication getPublication() {
		return publication;
	}

	public void setPublication(Publication publication) {
		this.publication = publication;
	}

	@XmlElement(name = "identifier", namespace = "http://purl.org/dc/terms/")
	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	@XmlElement(name = "control", namespace = "http://www.w3.org/2007/app")
	public Control getControl() {
		return control;
	}

	public void setControl(Control control) {
		this.control = control;
	}

	public Title getTitle() {
		return title;
	}

	public void setTitle(Title title) {
		this.title = title;
	}

	public Summary getSummary() {
		return summary;
	}

	public void setSummary(Summary summary) {
		this.summary = summary;
	}


	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<Link> getLink() {
		return link;
	}

	public void setLink(List<Link> link) {
		this.link = link;
	}

	public Content getContent() {
		return content;
	}

	public void setContent(Content content) {
		this.content = content;
	}

}
