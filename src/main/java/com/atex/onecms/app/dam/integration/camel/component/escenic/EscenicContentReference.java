package com.atex.onecms.app.dam.integration.camel.component.escenic;

public class EscenicContentReference extends EscenicContent {

	protected static final String MODEL_CONTENT_SUMMARY_PREFIX = "/content-summary/";
	protected static final String CONTENT_REFERENCE_TYPE = "article";
	protected static final String ESCENIC_IMAGE_TYPE = "picture";
	protected static final String ESCENIC_GALLERY_TYPE = "gallery";
	protected static final String ESCENIC_ARTICLE_TYPE = "news";
	protected static final String ESCENIC_SOUNDCLOUD_TYPE = "soundcloud";
	protected static final String ESCENIC_CODE_TYPE = "code";
	protected static final String ESCENIC_VIDEO_TYPE = "video";
	protected static final String ESCENIC_SOCIAL_EMBED_TYPE = "socialembed";

	private String type;
	private String code;
	private String thumbnailUrl;

	public String getThumbnailUrl() {
		return thumbnailUrl;
	}

	public void setThumbnailUrl(String thumbnailUrl) {
		this.thumbnailUrl = thumbnailUrl;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
