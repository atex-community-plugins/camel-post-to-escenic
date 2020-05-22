package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

import com.atex.onecms.app.dam.integration.camel.component.escenic.HrefAdapter;
import com.atex.onecms.app.dam.integration.camel.component.escenic.ValueAdapter;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Objects;

/**
 *
 * @author jakub
 */
@XmlRootElement
public class Link {
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Link link = (Link) o;
		return Objects.equals(type, link.type) &&
			Objects.equals(href, link.href) &&
			Objects.equals(identifier, link.identifier);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, href, identifier);
	}

	private String rel;
	private String type;
	private String title;
	private String href;
	private String thumbnail;
	private String state;
	private String group;
	private String identifier;
	private Payload payload;

	@XmlAttribute(name = "rel")
	public String getRel() {
		return rel;
	}
	@XmlAttribute(name = "type")
	public String getType() {
		return type;
	}

	@XmlAttribute(name = "href")
	@XmlJavaTypeAdapter(value= HrefAdapter.class)
	public String getHref() {
		return href;
	}

	@XmlAttribute(name = "title")
	public String getTitle() {
		return title;
	}

	@XmlAttribute(name = "thumbnail", namespace = "http://xmlns.escenic.com/2010/atom-metadata")
	public String getThumbnail() {
		return thumbnail;
	}

	public void setThumbnail(String thumbnail) {
		this.thumbnail = thumbnail;
	}

	@XmlAttribute(name = "state", namespace = "http://www.vizrt.com/atom-ext")
	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	@XmlAttribute(name = "group", namespace = "http://xmlns.escenic.com/2010/atom-metadata")
	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	@XmlAttribute(name = "identifier", namespace = "http://purl.org/dc/terms/")
	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	@XmlElement(name="payload", namespace="http://www.vizrt.com/types")
	public Payload getPayload() {
		return payload;
	}

	public void setPayload(Payload payload) {
		this.payload = payload;
	}

	public void setRel(String rel) {
		this.rel = rel;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setHref(String href) {
		this.href = href;
	}

	public void setTitle(String title) {
		this.title = title;
	}
}
