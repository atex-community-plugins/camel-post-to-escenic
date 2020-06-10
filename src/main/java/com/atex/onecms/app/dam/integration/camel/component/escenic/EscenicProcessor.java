package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.util.DamUtils;
import com.atex.onecms.content.*;
import com.polopoly.application.Application;
import com.polopoly.application.ApplicationInitEvent;
import com.polopoly.application.ApplicationOnAfterInitEvent;
import com.polopoly.application.IllegalApplicationStateException;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.client.CmClient;
import com.polopoly.cm.client.HttpFileServiceClient;
import com.polopoly.cm.policy.PolicyCMServer;
import com.polopoly.util.StringUtil;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.core.Response;

import javax.servlet.ServletContext;
import java.net.MalformedURLException;
import java.net.URL;

/**
 *
 * @author jakub
 */
@ApplicationInitEvent
public class EscenicProcessor implements Processor, ApplicationOnAfterInitEvent {

	private static CmClient cmClient;
	private static ContentManager contentManager;
	private static Application application;
	private static PolicyCMServer cmServer;
	private static EscenicUtils escenicUtils;
	private EscenicConfig escenicConfig;
	protected static final String PUBLISH_ACTION = "publish-to-escenic";
	protected static final String UNPUBLISH_ACTION = "unpublish-from-escenic";
	protected final Logger log = LoggerFactory.getLogger(getClass());

	private HttpFileServiceClient httpFileServiceClient;
	private String imageServiceUrl;
	private static final String IMAGE_SERVICE_URL_FALLBACK = "http://localhost:8080";

	@Override
	public void process(final Exchange exchange) throws Exception {
		log.debug("EscenicProcessor - start work");
		Response finalResponse = null;
		init();
		try {
			if (cmClient == null || contentManager == null) {
				exchange.getIn().setFault(true);
				return;
			}

			Message message = exchange.getIn();

			if (message == null) {
				exchange.getIn().setFault(true);
				throw new RuntimeException("Unable to process action due to error: message was null");
			}

			String action = null;
			Object actionHeader = message.getHeader("action");
			if (actionHeader instanceof String) {
				action = (String) actionHeader;
			}

			String contentIdString;
			if (message.getBody() instanceof String) {
				contentIdString = getContentId(exchange);
			} else {
				contentIdString = exchange.getIn().getHeader("contentId", ContentId.class).getKey();
			}

			if (StringUtils.isNotBlank(action)) {
				if(StringUtils.equalsIgnoreCase(action, PUBLISH_ACTION)) {
					log.info("Publishing content: " + contentIdString);
				} else if(StringUtils.equalsIgnoreCase(action, UNPUBLISH_ACTION)) {
					log.info("Unpublishing content: " + contentIdString);
				}
			}

			ContentId contentId = IdUtil.fromString(contentIdString);
			ContentResult cr = escenicUtils.checkAndExtractContentResult(contentId, contentManager);

			if (cr == null) {
				log.debug("content result was null, stopping the route.");
				exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
				return;
			}

			EscenicContentProcessor.getInstance().process(contentId, cr, action);
		} catch (Exception e){
			throw e;
		} finally {
			log.debug("Escenic processor - end work");
		}

	}

	private String getContentId(Exchange exchange) {
		String contentIdStr = (String) exchange.getIn().getBody();
		String tidiedContentIdStr = contentIdStr.replace("mutation:", "");
		int lastColon = tidiedContentIdStr.lastIndexOf(':');
		return tidiedContentIdStr.substring(0, lastColon);
	}

	@Override
	public void onAfterInit(ServletContext servletContext, String s, Application _application) {

			log.debug("Initializing EscenicProcessor");
			try {
				if (application == null) application = _application;
				if (contentManager == null) {
					final RepositoryClient repoClient = (RepositoryClient) application.getApplicationComponent(RepositoryClient.DEFAULT_COMPOUND_NAME);
					if (repoClient == null) {
						throw new CMException("No RepositoryClient named '"
							+ RepositoryClient.DEFAULT_COMPOUND_NAME
							+ "' present in Application '"
							+ application.getName() + "'");
					}
					contentManager = repoClient.getContentManager();
				}
				if (cmClient == null) {
					cmClient = application.getPreferredApplicationComponent(CmClient.class);;
					if (cmClient == null) {
						throw new CMException("No cmClient present in Application '"
							+ application.getName() + "'");
					}
				}
				if (cmServer == null) {
					cmServer = cmClient.getPolicyCMServer();;
				}

				log.debug("Started EscenicProcessor");
			} catch (Exception e) {
				log.error("Cannot start EscenicProcessor: " + e.getMessage(), e);
			} finally {
				log.debug("EscenicProcessor complete");
			}
	}

	private void init() {
		if (escenicConfig == null) {
			escenicConfig = EscenicApplication.getEscenicConfig();
		}

		if (escenicUtils == null) {
			escenicUtils = new EscenicUtils(escenicConfig);
		}

		if (httpFileServiceClient == null) {
			try {
				httpFileServiceClient = application.getPreferredApplicationComponent(HttpFileServiceClient.class);
			} catch (IllegalApplicationStateException e) {
				throw new RuntimeException(e);
			}
		}

		if (StringUtils.isBlank(imageServiceUrl)) {
			try {
				if (StringUtil.isEmpty(DamUtils.getDamUrl())) {
					imageServiceUrl = IMAGE_SERVICE_URL_FALLBACK;
					log.warn("desk.config.damUrl is not configured in connection.properties");
				} else {
					URL url = new URL(DamUtils.getDamUrl());
					imageServiceUrl = url.getProtocol() + "://" + url.getHost() + ":" + url.getPort();
				}
			} catch (MalformedURLException e) {
				log.error("Cannot configure the imageServiceUrl: " + e.getMessage());
				imageServiceUrl = IMAGE_SERVICE_URL_FALLBACK;
			}
		}

		EscenicContentProcessor.initInstance(contentManager, cmServer, escenicUtils, escenicConfig);
		EscenicGalleryProcessor.initInstance(contentManager, cmServer, escenicUtils, escenicConfig);
		EscenicImageProcessor.initInstance(contentManager, cmServer, escenicUtils, escenicConfig, httpFileServiceClient, imageServiceUrl);
		EscenicSmartEmbedProcessor.initInstance(contentManager, cmServer, escenicUtils, escenicConfig);
		EscenicSocialEmbedProcessor.initInstance(contentManager, cmServer, escenicUtils, escenicConfig);
		EscenicArticleProcessor.initInstance(contentManager, cmServer, escenicUtils, escenicConfig);
	}

}
