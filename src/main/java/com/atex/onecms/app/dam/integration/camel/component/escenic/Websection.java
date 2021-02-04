package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.content.ContentId;

/**
 *
 * @author jakub
 */
public class Websection {

	protected String escenicId;
	protected String publicationName;
	protected ContentId securityParentId;

	public Websection(String escenicId, String publicationName, ContentId securityParentId) {
		this.escenicId = escenicId;
		this.publicationName = publicationName;
		this.securityParentId = securityParentId;
	}

	public String getEscenicId() {
		return escenicId;
	}

	public void setEscenicId(String escenicId) {
		this.escenicId = escenicId;
	}

	public String getPublicationName() {
		return publicationName;
	}

	public void setPublicationName(String publicationName) {
		this.publicationName = publicationName;
	}

	public ContentId getSecurityParentId() {
		return securityParentId;
	}

	public void setSecurityParentId(ContentId securityParentId) {
		this.securityParentId = securityParentId;
	}
}
