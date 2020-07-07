package com.atex.onecms.app.dam.integration.camel.component.escenic.ws;

import com.atex.onecms.app.dam.integration.camel.component.escenic.EscenicConfig;
import com.atex.onecms.app.dam.integration.camel.component.escenic.EscenicContentToExternalReferenceContentConverter;
import com.atex.onecms.app.dam.integration.camel.component.escenic.EscenicUtils;
import com.atex.onecms.app.dam.integration.camel.component.escenic.config.EscenicConfigPolicy;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToDeserializeContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToRetrieveEscenicContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Entry;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Feed;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Link;
import com.atex.onecms.app.dam.standard.aspects.ExternalReferenceBean;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.ws.service.ErrorResponseException;
import com.atex.onecms.ws.service.WebServiceUtil;
import com.google.gson.Gson;
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
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.net.URI;
import java.util.Optional;

import static com.polopoly.service.cm.api.StatusCode.SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

@Path("/api")
@PerRequest
public class EscenicResource {

	private static Logger LOGGER = Logger.getLogger(EscenicResource.class.getName());
	private static final int TIMEOUT = 60 * 1000;
	private static final String DEFAULT_PAGE_NUMBER = "1";
	public static final String DEFAULT_QUERY = "(*)";

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

	private Caller latestCaller = null;

	@GET
	@Path("search")
	@Produces(MediaType.APPLICATION_JSON)
	public Response search(@QueryParam("query") String query, @QueryParam("pageNumber") String pageNumber, @QueryParam("filterQuery") String filterQuery,
						   @QueryParam("start") String start, @QueryParam("end") String end ,  @QueryParam("sort") String sort) throws ErrorResponseException {

		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Inside cross provider escenic search");
		}

		EscenicUtils escenicUtils = getEscenicUtils();

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

		EscenicUtils escenicUtils = getEscenicUtils();
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

		return Response.serverError().build();
	}

	@GET
	@Path("getContent")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getContent(@QueryParam("escenicLocation") String escenicLocation) throws ErrorResponseException {
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Getting full version of content from escenic for: " + escenicLocation);
		}

		EscenicUtils escenicUtils = getEscenicUtils();
		String escenicId = escenicUtils.extractIdFromLocation(escenicLocation);
		String xml = null;
		try {
			xml = escenicUtils.retrieveEscenicItem(getEscenicConfig().getContentUrl() + escenicId);
			Entry entry = escenicUtils.deserializeXml(xml);
			EscenicContentToExternalReferenceContentConverter escenicContentConverter = new EscenicContentToExternalReferenceContentConverter(getContentManager(), getCurrentCaller());
			ContentResult cr = escenicContentConverter.process(entry, escenicId);
			ExternalReferenceBean externalReferenceBean = (ExternalReferenceBean) cr.getContent().getContentData();
			if (externalReferenceBean != null) {
				JSONObject json = new JSONObject();
				json.put("_type", externalReferenceBean.getObjectType());
				json.put("id", IdUtil.toIdString(cr.getContentId().getContentId()));
				return Response.ok(json).build();
			}
		} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException | JSONException | NullPointerException e) {
			throw new ErrorResponseException(MediaType.APPLICATION_JSON_TYPE,
				e.getMessage(),
				SERVER_ERROR,
				SC_INTERNAL_SERVER_ERROR, e);
		}

		return Response.serverError().build();
	}

	@GET
	@Path("getThumbnail")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response getThumbnail(@QueryParam("url") String url) throws ErrorResponseException, IOException, URISyntaxException {
		EscenicUtils escenicUtils = getEscenicUtils();
		if (url != null) {

			url = URIUtil.encodeQuery(url);
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Sending the following query to escenic:\n" + url);
			}

			HttpGet request = new HttpGet(url);
			request.setHeader(escenicUtils.generateAuthenticationHeader(getEscenicConfig().getUsername(), getEscenicConfig().getPassword()));

			InputStream is = null;

			try {
				CloseableHttpResponse result = httpClient.execute(request);

				if (result != null && result.getStatusLine() != null && result.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

					String mimeType = result.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();

					final ContentType contentType = ContentType.create(
						Optional.ofNullable(mimeType)
							.orElse(ContentType.APPLICATION_OCTET_STREAM.getMimeType()));
					final InputStreamEntity inputStreamEntity = new InputStreamEntity(result.getEntity().getContent(), contentType);
					if (inputStreamEntity != null) {
						is = inputStreamEntity.getContent();

						byte[] imageData = StreamUtils.copyToByteArray(is);
						return Response.ok(new ByteArrayInputStream(imageData)).header(HttpHeaders.CONTENT_TYPE, mimeType).build();

					}
				} else {
					throw new ErrorResponseException(MediaType.APPLICATION_JSON_TYPE,
						"Failed to retrieve thumbnail: " + result.getStatusLine(),
						SERVER_ERROR,
						SC_INTERNAL_SERVER_ERROR);
				}
			} catch (Exception e) {
				throw new ErrorResponseException(MediaType.APPLICATION_JSON_TYPE,
				e.getMessage(),
				SERVER_ERROR,
				SC_INTERNAL_SERVER_ERROR, e);
			} finally {
				if (is != null) {
					is.close();
				}
			}
		}
		return Response.serverError().build();
	}

	public EscenicConfig getEscenicConfig() {
		EscenicConfig config;
		try {
			final CmClient cmClient = getApplication().getPreferredApplicationComponent(CmClient.class);
			final PolicyCMServer cmServer = cmClient.getPolicyCMServer();
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

	private EscenicUtils getEscenicUtils() {
		return new EscenicUtils(getEscenicConfig());
	}

	private ContentManager getContentManager() throws ErrorResponseException {
		return webServiceUtil.getContentManager();
	}

	protected Caller getCurrentCaller() {
		return Optional
			.ofNullable(latestCaller)
			.orElse(new Caller(new UserId("98")));
	}

}
