package com.atex.onecms.app.dam.integration.camel.component.escenic;

/**
 *
 * @author jakub
 */
public class EscenicImage extends EscenicContent {

	public static final String IMAGE_MODEL_CONTENT_TYPE = "/content-type/picture";
	public static final String IMAGE_MODEL_CONTENT_SUMMARY = "/content-summary/picture";
	public static final String THUMBNAIL_MODEL_TYPE = "/image/png";
	protected static final String IMAGE_TYPE = "image";

	private String title;
	private boolean topElement = false;
	private String thumbnailUrl;
	private String caption;

	public String getCaption() {
		return caption;
	}

	public void setCaption(String caption) {
		this.caption = caption;
	}

	public boolean isTopElement() {
		return topElement;
	}

	public void setTopElement(boolean topElement) {
		this.topElement = topElement;
	}

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
