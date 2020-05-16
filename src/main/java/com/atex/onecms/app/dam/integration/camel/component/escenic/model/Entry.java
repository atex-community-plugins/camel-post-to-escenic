package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 *
 * @author jakub
 */
@XmlRootElement(name="entry")
//@XmlType(propOrder={"title","content", "id", "link"})
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

	public Entry() {
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
