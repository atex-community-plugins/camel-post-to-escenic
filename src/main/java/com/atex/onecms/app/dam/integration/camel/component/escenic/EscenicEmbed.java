package com.atex.onecms.app.dam.integration.camel.component.escenic;

/**
 *
 * @author jakub
 */
public class EscenicEmbed extends EscenicContent{

	public static final String EMBED_MODEL_CONTENT_TYPE = "/content-type/socialembed";
	public static final String EMBED_MODEL_CONTENT_SUMMARY = "/content-summary/socialembed";
	protected static final String SOCIAL_EMBED_TYPE = "socialEmbed";
	private String embedCode;
	private String embedUrl;

	public EscenicEmbed() {
	}

	public String getEmbedUrl() {
		return embedUrl;
	}

	public String getEmbedCode() {
		return embedCode;
	}

	public void setEmbedCode(String embedCode) {
		this.embedCode = embedCode;
	}

	public void setEmbedUrl(String embedUrl) {
		this.embedUrl = embedUrl;
	}
}
