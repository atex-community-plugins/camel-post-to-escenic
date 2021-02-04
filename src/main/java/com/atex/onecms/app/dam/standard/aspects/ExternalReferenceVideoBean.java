package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

import java.util.Date;

@AspectDefinition(value = ExternalReferenceVideoBean.ASPECT_NAME, storeWithMainAspect = true)
public class ExternalReferenceVideoBean extends ExternalReferenceBean {
	public static final String ASPECT_NAME = "atex.onecms.external.video.reference";
	public static final String OBJECT_TYPE = "externalVideoReference";
	public static final String INPUT_TEMPLATE = "p.ExternalVideoReference";

	private String caption;

	public ExternalReferenceVideoBean() {
		super.setObjectType(OBJECT_TYPE);
		super.set_type(ASPECT_NAME);
		super.setCreationdate(new Date());
		super.setInputTemplate(INPUT_TEMPLATE);
	}
	
	public String getCaption() {
		return caption;
	}

	public void setCaption(String caption) {
		this.caption = caption;
	}

}
