package com.atex.onecms.app.dam.integration.camel.component.escenic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jakub
 */
public class EscenicConfig {

	private String apiUrl;
	private String username;
	private String password;
	private String binaryUrl;
	private String webSectionDimension;
	private String modelUrl;
	private String maxImgWidth;
	private String maxImgHeight;
	private String sectionListUsername;
	private String sectionListPassword;
	private String sectionListUrl;
	private String escenicTopLevelSearchUrl;
	private int timeout = 60*1000;

	public String getEscenicTopLevelSearchUrl() {
		return escenicTopLevelSearchUrl;
	}

	public void setEscenicTopLevelSearchUrl(String escenicTopLevelSearchUrl) {
		this.escenicTopLevelSearchUrl = escenicTopLevelSearchUrl;
	}



	public String getContentUrl() {
		return contentUrl;
	}

	public void setContentUrl(String contentUrl) {
		this.contentUrl = contentUrl;
	}

	private String contentUrl;

	public String getSectionListUsername() {
		return sectionListUsername;
	}

	public void setSectionListUsername(String sectionListUsername) {
		this.sectionListUsername = sectionListUsername;
	}

	public String getSectionListPassword() {
		return sectionListPassword;
	}

	public void setSectionListPassword(String sectionListPassword) {
		this.sectionListPassword = sectionListPassword;
	}

	public String getSectionListUrl() {
		return sectionListUrl;
	}

	public void setSectionListUrl(String sectionListUrl) {
		this.sectionListUrl = sectionListUrl;
	}

	public String getCropsMapping() {
		return cropsMapping;
	}

	public void setCropsMapping(String cropsMapping) {
		this.cropsMapping = cropsMapping;
	}

	private String cropsMapping;

	public String getMaxImgWidth() {
		return maxImgWidth;
	}

	public void setMaxImgWidth(String maxImgWidth) {
		this.maxImgWidth = maxImgWidth;
	}

	public String getMaxImgHeight() {
		return maxImgHeight;
	}

	public void setMaxImgHeight(String maxImgHeight) {
		this.maxImgHeight = maxImgHeight;
	}

	public EscenicConfig() {

	}

	public String getBinaryUrl() {
		return binaryUrl;
	}

	public void setBinaryUrl(String binaryUrl) {
		this.binaryUrl = binaryUrl;
	}

	public String getApiUrl() {
			return apiUrl;
		}

	public void setApiUrl(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getWebSectionDimension() {
		return this.webSectionDimension;
	}

	public void setWebSectionDimension(String webSectionDimension) {
		this.webSectionDimension = webSectionDimension;
	}

	public String getModelUrl() {
		return this.modelUrl;
	}

	public void setModelUrl(String modelUrl) {
		this.modelUrl = modelUrl;
	}

	public int getTimeout() {return timeout;}

	public void setTimeout(int timeout) {this.timeout = timeout;}
}


