package com.atex.onecms.app.dam.integration.camel.component.escenic;

/**
 *
 * @author jakub
 */
public class EscenicImage extends EscenicContent {

	public static final String IMAGE_MODEL_CONTENT_TYPE = "/content-type/picture";
	public static final String IMAGE_MODEL_CONTENT_SUMMARY = "/content-summary/picture";
	protected static final String IMAGE_TYPE = "image";

	private String title;

	private boolean topElement = false;

	public boolean isTopElement() {
		return topElement;
	}

	public void setTopElement(boolean topElement) {
		this.topElement = topElement;
	}

	private String thumbnailUrl;

	public String getThumbnailUrl() {
		return thumbnailUrl;
	}

	public void setThumbnailUrl(String thumbnailUrl) {
		this.thumbnailUrl = thumbnailUrl;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}
}
