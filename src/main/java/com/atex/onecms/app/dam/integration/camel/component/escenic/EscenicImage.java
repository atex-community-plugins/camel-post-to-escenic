package com.atex.onecms.app.dam.integration.camel.component.escenic;

/**
 *
 * @author jakub
 */
public class EscenicImage extends EscenicContent {

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
