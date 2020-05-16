package com.atex.onecms.app.dam.integration.camel.component.escenic;

/**
 *
 * @author jakub
 */
public class EscenicEmbed extends EscenicContent{


	private String embedCode;

	public String getEmbedUrl() {
		return embedUrl;
	}

	private String embedUrl;

	public String getEmbedCode() {
		return embedCode;
	}

	public void setEmbedCode(String embedCode) {
		this.embedCode = embedCode;
	}



	public EscenicEmbed() {

	}

	public void setEmbedUrl(String embedUrl) {
		this.embedUrl = embedUrl;
	}
}
