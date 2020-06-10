package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.integration.camel.component.escenic.config.EscenicConfigPolicy;
import com.polopoly.application.Application;
import com.polopoly.application.ApplicationInitEvent;
import com.polopoly.application.ApplicationOnAfterInitEvent;
import com.polopoly.application.IllegalApplicationStateException;
import com.polopoly.cm.ExternalContentId;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.client.CmClient;
import com.polopoly.cm.policy.PolicyCMServer;

import javax.servlet.ServletContext;
import java.util.logging.Logger;

@ApplicationInitEvent
public class EscenicApplication implements ApplicationOnAfterInitEvent {

		private static final Logger LOGGER = Logger.getLogger(EscenicApplication.class.getName());
		private static Application application;

		@Override
		public void onAfterInit(ServletContext servletContext, String name, Application _application) {
			application = _application;
		}

		public static Application getApplication() {
			return application;
		}

		public static EscenicConfig getEscenicConfig() {
			EscenicConfig config;
			try {
				final CmClient cmClient = application.getPreferredApplicationComponent(CmClient.class);
				final PolicyCMServer cmServer = cmClient.getPolicyCMServer();
				EscenicConfigPolicy policy = (EscenicConfigPolicy) cmServer.getPolicy(new ExternalContentId(EscenicConfigPolicy.CONFIG_EXTERNAL_ID));
				if (policy == null) throw new CMException("No escenic configuration found with id: " + EscenicConfigPolicy.CONFIG_EXTERNAL_ID);
				config = policy.getConfig();
				return config;

			} catch (CMException | IllegalApplicationStateException e) {
				LOGGER.severe("Failed to get Escenic Application: " + e.getMessage());
				return null;
			}
		}
}