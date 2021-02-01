package com.atex.onecms.app.dam.integration.camel.component.escenic.ws;

import static com.polopoly.service.cm.api.StatusCode.SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.HeaderParam;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.atex.onecms.app.dam.integration.camel.component.escenic.EscenicConfig;
import com.atex.onecms.app.dam.integration.camel.component.escenic.EscenicContentToExternalReferenceContentConverter;
import com.atex.onecms.app.dam.integration.camel.component.escenic.EscenicUtils;
import com.atex.onecms.app.dam.integration.camel.component.escenic.config.EscenicConfigPolicy;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToDeserializeContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToRetrieveEscenicContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Control;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Entry;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Feed;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Link;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.State;
import com.atex.onecms.app.dam.standard.aspects.ExternalReferenceBean;
import com.atex.onecms.app.dam.standard.aspects.ExternalReferenceVideoBean;

import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.SubjectUtil;

import com.atex.onecms.ws.activity.ActivityException;
import com.atex.onecms.ws.activity.ActivityInfo;
import com.atex.onecms.ws.activity.ActivityServiceSecured;
import com.atex.onecms.ws.activity.ApplicationInfo;
import com.atex.onecms.ws.service.AuthenticationUtil;
import com.atex.onecms.ws.service.ErrorResponseException;
import com.atex.onecms.ws.service.WebServiceUtil;

import com.google.gson.Gson;
import com.google.inject.Inject;

import com.polopoly.application.Application;
import com.polopoly.application.IllegalApplicationStateException;
import com.polopoly.application.servlet.ApplicationServletUtil;

import com.polopoly.cm.ExternalContentId;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.client.CmClient;
import com.polopoly.cm.policy.PolicyCMServer;

import com.polopoly.user.server.Caller;
import com.polopoly.user.server.UserId;

import com.sun.jersey.spi.resource.PerRequest;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.lang.StringUtils;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.springframework.util.StreamUtils;

@Path("/api")
@PerRequest
public class EscenicResource {

	private static Logger LOGGER = Logger.getLogger(EscenicResource.class.getName());
	private static final int TIMEOUT = 60 * 1000;
	private static final String DEFAULT_PAGE_NUMBER = "1";
	public static final String DEFAULT_QUERY = "(*)";
	public static final String ATEX_ACT_APP = "atex.act";

	private static final RequestConfig config = RequestConfig.custom()
		.setConnectTimeout(TIMEOUT)
		.setConnectionRequestTimeout(TIMEOUT)
		.setSocketTimeout(TIMEOUT).build();

	protected CloseableHttpClient httpClient =HttpClients.custom()
		.disableAutomaticRetries()
		.disableCookieManagement()
		.disableContentCompression()
		.setDefaultRequestConfig(config)
		.build();

	@Context
	private ServletContext servletContext;

	@Context
	private WebServiceUtil webServiceUtil;

	@Inject
	ActivityServiceSecured activityServiceSecured;

	private Caller latestCaller = null;

	@HeaderParam("X-Auth-Token") String authToken;

	@Context
	private javax.ws.rs.core.HttpHeaders httpHeaders;

	class TokenData {
		public String token;
	}

	@POST
	@Path("checkAndLockContent")
	@Produces(MediaType.APPLICATION_JSON)
	public Response checkAndLockContent(InputStream dataStream, @QueryParam("contentIdString") String contentIdString) throws ErrorResponseException {

		try {
			Gson gson = new Gson();
			BufferedReader reader = new BufferedReader(new InputStreamReader(dataStream));
			EscenicResource.TokenData data = gson.fromJson(reader, EscenicResource.TokenData.class);
			authToken = data.token;

			Caller caller = AuthenticationUtil.getLoggedInCaller(getCmClient().getUserServer(), authToken, httpHeaders.getMediaType(), false);
			ActivityInfo activityInfo = activityServiceSecured.get(SubjectUtil.fromCaller(caller), contentIdString);
			LOGGER.finest("activityInfo: " + activityInfo);

			if (activityInfo != null && (activityInfo.isEmpty() || !isLockedByAct(activityInfo)) ) {
				ApplicationInfo appInfo = new ApplicationInfo();
				appInfo.setActivity("edit");
				appInfo.setTimestamp(System.currentTimeMillis());
				activityServiceSecured.write(SubjectUtil.fromCaller(caller), contentIdString, caller.getUserId().getPrincipalIdString(), "atex.act", appInfo);
				return Response.ok().build();
			}
		} catch (ActivityException | IllegalApplicationStateException e) {
			return Response.serverError().entity(e.getMessage()).build();
		}

		return Response.serverError().entity("Content locked - Please ensure that content is not locked before publishing.").build();
	}

	@GET
	@Path("search")
	@Produces(MediaType.APPLICATION_JSON)
	public Response search(@QueryParam("query") String query, @QueryParam("pageNumber") String pageNumber, @QueryParam("filterQuery") String filterQuery,
						   @QueryParam("start") String start, @QueryParam("end") String end ,  @QueryParam("sort") String sort) throws ErrorResponseException {

		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Inside cross provider escenic search");
		}

		EscenicUtils escenicUtils = null;
		try {
			escenicUtils = getEscenicUtils();
		} catch (IllegalApplicationStateException e) {
			e.printStackTrace();
		}
		try {
			query = URIUtil.decode(query);
		} catch (URIException e) {
			e.printStackTrace();
		}

		query =	escenicUtils.getQuery(query);

		String baseSearchUrl = getEscenicConfig().getEscenicTopLevelSearchUrl();
		String location = baseSearchUrl +  query;
		if (StringUtils.isNotBlank(sort)) {
			location += sort;
		}

		if (StringUtils.isBlank(pageNumber)) {
			pageNumber = DEFAULT_PAGE_NUMBER;
		}
		location += "?pw=" + pageNumber;

		if (StringUtils.isNotBlank(filterQuery)) {
			location += "&filters=" + filterQuery;
		}

		if (StringUtils.isNotEmpty(start) && StringUtils.isNotEmpty(end)) {
			location += "&start=" + start;
			location += "&end=" + end;
		}

		HttpGet request = null;
		try {
			location = URIUtil.encodeQuery(location);
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Sending the following query to escenic:\n" + location);
			}
			request = new HttpGet(location);
			request.setHeader(escenicUtils.generateAuthenticationHeader(getEscenicConfig().getUsername(), getEscenicConfig().getPassword()));
			CloseableHttpResponse result = httpClient.execute(request);

			if (result != null && result.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				String xml = null;
				if (result.getEntity() != null) {
					xml = EntityUtils.toString(result.getEntity());
				}

				if (StringUtils.isNotBlank(xml)) {

					xml = escenicUtils.createSearchGroups(xml);

					Feed feed = null;
					try {
						JAXBContext jaxbContext = JAXBContext.newInstance(Feed.class);
						Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
						feed = (Feed) unmarshaller.unmarshal(new StringReader(xml));
						escenicUtils.processSearchInfo(feed);
						escenicUtils.processFacetInfo(feed);
						Gson gson = new Gson();
						String json = gson.toJson(feed);
						if (LOGGER.isLoggable(Level.FINEST)) {
							LOGGER.finest("json result of escenic search:\n" + json);
						}
						return Response.ok(json).build();
					} catch (JAXBException e) {
						throw new FailedToDeserializeContentException("Failed to deserialize content: " + e);
					}

				}
			} else {
				LOGGER.log(Level.SEVERE, "Error response when searching escenic: " + result.getStatusLine());
				return Response.serverError().build();
			}
		} catch (IOException | URISyntaxException | EscenicException e) {
			throw new ErrorResponseException(MediaType.APPLICATION_JSON_TYPE,
				e.getMessage(),
				SERVER_ERROR,
				SC_INTERNAL_SERVER_ERROR, e);
		} finally {
			if (request != null) {
				request.releaseConnection();
			}
		}

		return Response.serverError().build();

	}

	@GET
	@Path("openPreview")
	@Produces(MediaType.APPLICATION_JSON)
	public Response openPreview(@QueryParam("escenicLocation") String escenicLocation) throws ErrorResponseException {
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Opening preview for:\n" + escenicLocation);
		}

		EscenicUtils escenicUtils = null;
		try {
			escenicUtils = getEscenicUtils();
		} catch (IllegalApplicationStateException e) {
			e.printStackTrace();
		}
		String escenicId = escenicUtils.extractIdFromLocation(escenicLocation);
		String xml = null;
		String previewUrl = null;

		try {
			xml = escenicUtils.retrieveEscenicItem(getEscenicConfig().getContentUrl() + escenicId);
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("retrieved content for preview generation:\n" + xml);
			}
			Entry entry = escenicUtils.deserializeXml(xml);

			if (entry != null) {
				if (entry.getLink() != null) {
					for (Link link : entry.getLink()) {
						if (link != null && StringUtils.equalsIgnoreCase(link.getRel(), "alternate")) {
							previewUrl = link.getHref();
						}
					}
				}
			}

			//load escenic content and extract the value of the preview url
			if (StringUtils.isNotBlank(previewUrl)) {
				URI uri = new URI(previewUrl);
				return Response.seeOther(uri).contentLocation(uri).build();
			}
		} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException | URISyntaxException e) {
			throw new ErrorResponseException(MediaType.APPLICATION_JSON_TYPE,
				e.getMessage(),
				SERVER_ERROR,
				SC_INTERNAL_SERVER_ERROR, e);
		}

		return Response.serverError().entity("No preview link available - is it a draft content? (No Alternate link in content atom entry)").build();
	}

	@GET
	@Path("getContent")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getContent(@QueryParam("escenicLocation") String escenicLocation, @QueryParam("contentType") String contentType) throws ErrorResponseException, IllegalApplicationStateException {
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Loading whole content from escenic for: " + escenicLocation);
		}

		Caller caller = AuthenticationUtil.getLoggedInCaller(getCmClient().getUserServer(), authToken, httpHeaders.getMediaType(), false);
		EscenicUtils escenicUtils = getEscenicUtils();
		String escenicId = escenicUtils.extractIdFromLocation(escenicLocation);
		String xml = null;
		try {
			xml = escenicUtils.retrieveEscenicItem(getEscenicConfig().getContentUrl() + escenicId);
			Entry entry = escenicUtils.deserializeXml(xml);

			if (entry != null) {

				if (escenicUtils.isSupportedContentType(contentType)) {
					Control control = entry.getControl();
					java.util.List<State> stateList = control.getState();
					boolean validState = false;
					boolean isExpired = false;
					if (stateList != null) {
						for (State state : stateList) {
							if (StringUtils.equalsIgnoreCase(state.getName(),"published") || StringUtils.equalsIgnoreCase(state.getName(), "draft-published")) {
								validState = true;
							}
							if (StringUtils.equalsIgnoreCase(state.getName(), "post-active")) {
								isExpired = true;
							}
						}
					}

					if (validState && !isExpired) {
						EscenicContentToExternalReferenceContentConverter escenicContentConverter =
							new EscenicContentToExternalReferenceContentConverter(getContentManager());
						try {
							ContentResult cr = escenicContentConverter.process(escenicUtils, entry, escenicId, caller);
							if (cr.getContent().getContentData() instanceof ExternalReferenceBean) {
								ExternalReferenceBean externalReferenceBean = (ExternalReferenceBean) cr.getContent().getContentData();
								return processAndReturn(externalReferenceBean, IdUtil.toIdString(cr.getContentId().getContentId()));
							} else {
								ExternalReferenceVideoBean externalReferenceVideoBean = (ExternalReferenceVideoBean) cr.getContent().getContentData();
								return processAndReturn(externalReferenceVideoBean, IdUtil.toIdString(cr.getContentId().getContentId()));
							}
						} catch (EscenicException e) {
							throw new ErrorResponseException(MediaType.APPLICATION_JSON_TYPE,
								e.getMessage(),
								SERVER_ERROR,
								SC_INTERNAL_SERVER_ERROR, e);
						}
					}
				}
			}

		} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException | JSONException | IOException e) {
			throw new ErrorResponseException(MediaType.APPLICATION_JSON_TYPE,
				e.getMessage(),
				SERVER_ERROR,
				SC_INTERNAL_SERVER_ERROR, e);
		}

		return Response.serverError().build();
	}


	private Response processAndReturn(ExternalReferenceBean externalReferenceBean, String contentIdString) throws JSONException {
		if (externalReferenceBean != null) {
			JSONObject json = new JSONObject();
			json.put("id", contentIdString);
			json.put("_type", externalReferenceBean.getObjectType());
			return Response.ok(json).build();
		}
		return Response.serverError().build();
	}

	@GET
	@Path("getThumbnail")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response getThumbnail(@QueryParam("url") String url) throws ErrorResponseException, IOException {
		EscenicUtils escenicUtils = null;
		try {
			escenicUtils = getEscenicUtils();
		} catch (IllegalApplicationStateException e) {
			throw new ErrorResponseException(MediaType.APPLICATION_JSON_TYPE,
				"Failed to retrieve escenicUtils - unable to proceed.",
				SERVER_ERROR,
				SC_INTERNAL_SERVER_ERROR);
		}

		try (CloseableHttpResponse result = escenicUtils.getImageThumbnailResponse(url)) {

			if (result != null && result.getStatusLine() != null && result.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				String mimeType = result.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();

				//todo change this in the future to avoid loading the thumbnails into memory
				final InputStreamEntity inputStreamEntity = escenicUtils.generateImageEntity(result.getEntity().getContent(), mimeType);

				if (inputStreamEntity != null) {
					try (InputStream is = inputStreamEntity.getContent()) {
						byte[] imageData = StreamUtils.copyToByteArray(is);
						return Response.ok(new ByteArrayInputStream(imageData)).header(HttpHeaders.CONTENT_TYPE, mimeType).build();
					} catch (Exception e ){
						throw new ErrorResponseException(MediaType.APPLICATION_JSON_TYPE,
							"Failed to read image entity: " + e.getMessage(),
							SERVER_ERROR,
							SC_INTERNAL_SERVER_ERROR);
					}
				}
			} else {
				throw new ErrorResponseException(MediaType.APPLICATION_JSON_TYPE,
					"Failed to retrieve thumbnail: " + result.getStatusLine(),
					SERVER_ERROR,
					SC_INTERNAL_SERVER_ERROR);
			}
		}

		return Response.serverError().build();
	}

	private CmClient getCmClient() throws IllegalApplicationStateException {
		return getApplication().getPreferredApplicationComponent(CmClient.class);
	}

	public EscenicConfig getEscenicConfig() {
		EscenicConfig config;
		try {
			final PolicyCMServer cmServer = getCmClient().getPolicyCMServer();
			EscenicConfigPolicy policy = (EscenicConfigPolicy) cmServer.getPolicy(new ExternalContentId(EscenicConfigPolicy.CONFIG_EXTERNAL_ID));
			if (policy == null) throw new CMException("No escenic configuration found with id: " + EscenicConfigPolicy.CONFIG_EXTERNAL_ID);
			config = policy.getConfig();
			return config;

		} catch (CMException | IllegalApplicationStateException e) {
			LOGGER.warning("Escenic Application: " + e.getMessage());
			return null;
		}
	}

	private Application getApplication() {
		return ApplicationServletUtil.getApplication(servletContext);
	}

	private EscenicUtils getEscenicUtils() throws ErrorResponseException, IllegalApplicationStateException {
		return new EscenicUtils(getEscenicConfig(), getContentManager(), getCmClient().getPolicyCMServer());
	}

	private ContentManager getContentManager() throws ErrorResponseException {
		return webServiceUtil.getContentManager();
	}

	private boolean isLockedByAct(ActivityInfo activityInfo) {
		AtomicBoolean found = new AtomicBoolean(false);
		if (activityInfo != null && !activityInfo.getUsers().isEmpty()) {
			activityInfo.getUsers().forEach((userKey, userActivities) -> {
				if (userActivities != null) {
					userActivities.getApplications().forEach((applicationKey, applicationInfo) -> {
						if (applicationKey != null) {
							if (StringUtils.equalsIgnoreCase(applicationKey, ATEX_ACT_APP)) {
								found.set(true);
							}
						}
					});
				}
			});
		}
		return found.get();
	}

}
