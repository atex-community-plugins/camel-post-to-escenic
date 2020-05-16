package com.atex.onecms.app.dam.integration.camel.component.escenic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jakub
 */
public class EscenicConfig {

		protected final Logger log = LoggerFactory.getLogger(getClass());

		private String apiUrl;
		private String username;
		private String password;
		private String binaryUrl;

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

}


