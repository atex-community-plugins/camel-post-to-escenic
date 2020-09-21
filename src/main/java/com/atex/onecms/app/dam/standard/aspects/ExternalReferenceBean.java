package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(value = ExternalReferenceBean.ASPECT_NAME, storeWithMainAspect = true)
public class ExternalReferenceBean extends OneArticleBean {
	public static final String ASPECT_NAME = "atex.onecms.external.reference";
	public static final String OBJECT_TYPE = "externalReference";
	public static final String INPUT_TEMPLATE = "p.ExternalReference";

	private String title;
	private String externalReferenceContentType;
	private String state;
	private String location;
	private String externalReferenceId;
	private String thumbnailUrl;

	public ExternalReferenceBean() {
		super.setObjectType(OBJECT_TYPE);
		super.set_type(ASPECT_NAME);
		super.setCreationdate(new Date());
		super.setInputTemplate(INPUT_TEMPLATE);
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getExternalReferenceContentType() {
		return externalReferenceContentType;
	}

	public void setExternalReferenceContentType(String externalReferenceContentType) {
		this.externalReferenceContentType = externalReferenceContentType;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getExternalReferenceId() {
		return externalReferenceId;
	}

	public void setExternalReferenceId(String externalReferenceId) {
		this.externalReferenceId = externalReferenceId;
	}

	public String getThumbnailUrl() {
		return thumbnailUrl;
	}

	public void setThumbnailUrl(String thumbnailUrl) {
		this.thumbnailUrl = thumbnailUrl;
	}

}
