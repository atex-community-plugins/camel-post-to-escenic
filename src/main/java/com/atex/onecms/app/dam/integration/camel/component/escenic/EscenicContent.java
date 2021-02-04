package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Link;
import com.atex.onecms.content.ContentId;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jakub
 */
public class EscenicContent {

	private String escenicLocation;
	private String escenicId;
	private ContentId onecmsContentId;
	private String title;
	private boolean inlineElement = true;
	private boolean topElement = false;
	private List<Link> links = new ArrayList<>();

	//the id generated for linked content
	private String id;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<Link> getLinks() {
		return links;
	}

	public void setLinks(List<Link> links) {
		this.links = links;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getEscenicLocation() {
		return escenicLocation;
	}

	public void setEscenicLocation(String escenicLocation) {
		this.escenicLocation = escenicLocation;
	}

	public String getEscenicId() {
		return escenicId;
	}

	public ContentId getOnecmsContentId() {
		return onecmsContentId;
	}

	public void setOnecmsContentId(ContentId onecmsContentId) {
		this.onecmsContentId = onecmsContentId;
	}

	public void setEscenicId(String escenicId) {
		this.escenicId = escenicId;
	}

	public boolean isInlineElement() {
		return inlineElement;
	}

	public void setInlineElement(boolean inlineElement) {
		this.inlineElement = inlineElement;
	}

	public boolean isTopElement() {
		return topElement;
	}

	public void setTopElement(boolean topElement) {
		this.topElement = topElement;
	}
}
