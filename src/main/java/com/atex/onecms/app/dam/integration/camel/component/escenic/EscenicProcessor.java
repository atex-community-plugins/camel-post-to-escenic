package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.ContentLockedException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.util.DamUtils;
import com.atex.onecms.content.*;
import com.atex.onecms.ws.service.AuthenticationUtil;
import com.polopoly.application.Application;
import com.polopoly.application.ApplicationInitEvent;
import com.polopoly.application.ApplicationOnAfterInitEvent;
import com.polopoly.application.IllegalApplicationStateException;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.client.CmClient;
import com.polopoly.cm.client.HttpFileServiceClient;
import com.polopoly.cm.policy.PolicyCMServer;
import com.polopoly.user.server.Caller;
import com.polopoly.util.StringUtil;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import javax.ws.rs.core.Response;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

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

	private static final Logger LOGGER = Logger.getLogger(EscenicProcessor.class.getName());
	private HttpFileServiceClient httpFileServiceClient;
	private String imageServiceUrl;
	private static final String IMAGE_SERVICE_URL_FALLBACK = "http://localhost:8080";

	private boolean tryToLockContent(ContentId contentId) throws EscenicException {
		LOGGER.fine("Generating request to check lock info for content: " + IdUtil.toIdString(contentId));
		String token = "{ \"token\": \"" + AuthenticationUtil.getAuthToken(EscenicContentProcessor.getInstance().getCurrentCaller()) + "\"}";
		StringEntity entity = new StringEntity(token, StandardCharsets.UTF_8);
		entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
		String location =  DamUtils.getRemoteApiUrl() + "escenic/api/checkAndLockContent?contentIdString=" + IdUtil.toIdString(contentId);
		HttpPost request = new HttpPost(location);
		request.setEntity(entity);
		try (final CloseableHttpResponse result = escenicUtils.getHttpClient().execute(request);) {
			if (result != null) {
				return result.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
			}
		} catch (IOException e) {
			throw new EscenicException("An error occurred while attempting to lock content: " + IdUtil.toIdString(contentId));
		}

		return false;
	}

	private void unlockContent(ContentId contentId) throws EscenicException {
		LOGGER.finest("Releasing lock for content: " + IdUtil.toIdString(contentId));
		Caller caller = EscenicContentProcessor.getInstance().getCurrentCaller();
		String url = DamUtils.getDamUrl() + "content/unlock/"+ IdUtil.toIdString(contentId) +"/" + caller.getUserId().getPrincipalIdString() + "/atex.act";
		String token = "{\"token\": \"" + AuthenticationUtil.getAuthToken(EscenicContentProcessor.getInstance().getCurrentCaller()) + "\"}";
		HttpPost request = new HttpPost(url);
		StringEntity entity = new StringEntity(token, StandardCharsets.UTF_8);
		entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
		request.setEntity(entity);
		try (final CloseableHttpResponse result = escenicUtils.getHttpClient().execute(request);) {

		} catch (IOException e) {
			throw new EscenicException("An error occurred while attempting to unlock content: " + IdUtil.toIdString(contentId));
		}
	}

	@Override
	public void process(final Exchange exchange) throws Exception {
		LOGGER.finest("EscenicProcessor - start work");
		Response finalResponse = null;
		init();
		ContentId contentId = null;
		boolean locked = false;
		try {
			if (cmClient == null || contentManager == null) {
				LOGGER.severe("Unable to proceed - cmClient or contentManager was null, stopping the route");
				throw new RuntimeException("Unable to proceed - cmClient or contentManager was null, stopping the route");
			}

			Message message = exchange.getIn();

			if (message == null) {
				LOGGER.severe("Unable to proceed - message was null, stopping the route");
				throw new RuntimeException("Unable to process action due to error: message was null");
			}

			String action = null;
			Object actionHeader = message.getHeader("action");
			if (actionHeader instanceof String) {
				action = (String) actionHeader;
			}

			Object callerHeader = message.getHeader("caller");
			if (callerHeader instanceof Caller) {
				EscenicContentProcessor.getInstance().latestCaller = (Caller) callerHeader;
			}

			if (EscenicContentProcessor.getInstance().getCurrentCaller() == null) {
				throw new RuntimeException("The current caller is not present. Unable to proceed.");
			}

			String contentIdString;
			if (message.getBody() instanceof String) {
				contentIdString = getContentId(exchange);
			} else if (exchange.getIn() != null && exchange.getIn().getHeader("contentId", ContentId.class) != null) {
				contentIdString = exchange.getIn().getHeader("contentId", ContentId.class).getKey();
			} else {
				LOGGER.severe("Unable to get the content id for message: " + message);
				throw new RuntimeException("Unable to get the content id for message " + message);
			}

			contentId = IdUtil.fromString(contentIdString);

			if (!tryToLockContent(contentId)) {
				throw new ContentLockedException("Content with id: " + contentIdString + " is currently locked. Unable to publish to escenic.");
			}

			locked = true;

			if (StringUtils.isNotBlank(action)) {
				if (StringUtils.equalsIgnoreCase(action, PUBLISH_ACTION)) {
					LOGGER.info("Publishing content: " + contentIdString);
				} else if (StringUtils.equalsIgnoreCase(action, UNPUBLISH_ACTION)) {
					LOGGER.info("Unpublishing content: " + contentIdString);
				}
			}

			ContentResult cr = escenicUtils.checkAndExtractContentResult(contentId, contentManager);

			if (cr == null) {
				LOGGER.severe("content result was null, stopping the route.");
				exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
				return;
			}
			
			EscenicContentProcessor.getInstance().process(contentId, cr, action);
		} catch (Exception e){
			LOGGER.log(Level.SEVERE, "Failed due to: " + e.getCause() + " - " + e.getMessage(), e);
			throw e;
		} finally {
			//only unlock content if it was locked by the escenic plugin
			if (locked) {
				unlockContent(contentId);
			}
			LOGGER.finest("Escenic processor - end work");
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

		LOGGER.finest("Initializing EscenicProcessor");
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

			LOGGER.finest("Started EscenicProcessor");
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Cannot start EscenicProcessor: " + e.getMessage(), e);
		} finally {
			LOGGER.finest("EscenicProcessor complete");
		}
	}

	private void init() {
		if (escenicConfig == null) {
			escenicConfig = EscenicApplication.getEscenicConfig();
		}

		if (escenicUtils == null) {
			escenicUtils = new EscenicUtils(escenicConfig, contentManager, cmServer);
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
					LOGGER.warning("desk.config.damUrl is not configured in connection.properties");
				} else {
					URL url = new URL(DamUtils.getDamUrl());
					imageServiceUrl = url.getProtocol() + "://" + url.getHost() + ":" + url.getPort();
				}
			} catch (MalformedURLException e) {
				LOGGER.severe("Cannot configure the imageServiceUrl: " + e.getMessage());
				imageServiceUrl = IMAGE_SERVICE_URL_FALLBACK;
			}
		}

		EscenicContentProcessor.initInstance(escenicUtils);
		EscenicGalleryProcessor.initInstance(escenicUtils);
		EscenicImageProcessor.initInstance(escenicUtils, httpFileServiceClient, imageServiceUrl);
		EscenicSmartEmbedProcessor.initInstance(escenicUtils);
		EscenicSocialEmbedProcessor.initInstance(escenicUtils);
		EscenicArticleProcessor.initInstance(escenicUtils);
		EscenicRelatedContentProcessor.initInstance(escenicUtils);
		EscenicTagProcessor.initInstance(escenicUtils);
	}
}
