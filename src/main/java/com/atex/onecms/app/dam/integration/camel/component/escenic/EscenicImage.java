package com.atex.onecms.app.dam.integration.camel.component.escenic;

/**
 *
 * @author jakub
 */
public class EscenicImage extends EscenicContent {

	public static final String IMAGE_MODEL_CONTENT_TYPE = "/model/content-type/picture";
	public static final String IMAGE_MODEL_CONTENT_SUMMARY = "/model/content-summary/picture";
	public static final String THUMBNAIL_MODEL_TYPE = "/image/png";
	protected static final String IMAGE_TYPE = "image";

	private String title;
	private String thumbnailUrl;
	private String caption;
	private boolean noUseWeb = false;

	public boolean isNoUseWeb() {
		return noUseWeb;
	}

	public void setNoUseWeb(boolean noUseWeb) {
		this.noUseWeb = noUseWeb;
	}

	public String getCaption() {
		return caption;
	}

	public void setCaption(String caption) {
		this.caption = caption;
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
