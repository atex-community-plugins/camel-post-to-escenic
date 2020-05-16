package com.atex.onecms.app.dam.integration.camel.component.escenic;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jakub
 */
public class EscenicGallery extends EscenicContent {
	private String thumbnailUrl;
	private List<EscenicContent> contentList = new ArrayList<>();

	private boolean topElement = false;

	public boolean isTopElement() {
		return topElement;
	}

	public void setTopElement(boolean topElement) {
		this.topElement = topElement;
	}

	public List<EscenicContent> getContentList() {
		return contentList;
	}

	public void setContentList(List<EscenicContent> contentList) {
		this.contentList = contentList;
	}

	public String getThumbnailUrl() {
		return thumbnailUrl;
	}

	public void setThumbnailUrl(String thumbnailUrl) {
		this.thumbnailUrl = thumbnailUrl;
	}
}
