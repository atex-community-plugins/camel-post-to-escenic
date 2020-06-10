package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.engagement.EngagementElement;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToDeserializeContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToRetrieveEscenicContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToSendContentToEscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.*;
import com.atex.onecms.app.dam.standard.aspects.*;
import com.atex.onecms.content.*;
import com.atex.onecms.content.repository.StorageException;
import com.atex.plugins.baseline.util.MimeUtil;
import com.atex.plugins.structured.text.StructuredText;
import com.google.common.base.CharMatcher;
import com.sun.xml.bind.marshaller.CharacterEscapeHandler;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.xml.bind.*;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * @author jakub
 */
public class EscenicUtils {

	private static String AUTH_PREFIX = "Basic ";
	private static Logger log = LoggerFactory.getLogger(EscenicUtils.class);
	protected EscenicConfig escenicConfig;
	protected static final String INLINE_RELATIONS_GROUP = "com.escenic.inlineRelations";
	protected static final String PICTURE_RELATIONS_GROUP = "pictureRel";
	protected static final String THUMBNAIL_RELATION_GROUP = "thumbnail";
	protected static final String APP_ATOM_XML = "application/atom+xml";
	protected static final String ATOM_APP_ENTRY_TYPE = APP_ATOM_XML + "; type=entry";
	protected static final String RELATED = "related";
	protected static final String PUBLISHED_STATE = "published";

	public CloseableHttpClient getHttpClient() {
		return httpClient;
	}

	protected CloseableHttpClient httpClient;
	private static final int TIMEOUT = 60 * 1000;
	private static final RequestConfig config = RequestConfig.custom()
		.setConnectTimeout(TIMEOUT)
		.setConnectionRequestTimeout(TIMEOUT)
		.setSocketTimeout(TIMEOUT).build();


	public EscenicUtils(EscenicConfig escenicConfig) {
		this.escenicConfig = escenicConfig;
		this.httpClient = HttpClients.custom()
			.disableAutomaticRetries()
			.disableCookieManagement()
			.disableContentCompression()
			.setDefaultRequestConfig(config)
			.build();
	}

	public String retrieveSectionList(String location) throws FailedToRetrieveEscenicContentException {
		if (StringUtils.isBlank(location)) {
			throw new RuntimeException("Unable to read section list url from config");
		}

		HttpGet request = new HttpGet(location);


		//todo escenicConfig get secrion username / pw
		generateAuthenticationHeader(escenicConfig.getSectionListUsername(), escenicConfig.getSectionListPassword());
		request.setHeader(new BasicHeader(HttpHeaders.AUTHORIZATION, AUTH_PREFIX + Base64.getEncoder().encodeToString(("cannonball" + ":" + "1amaM0viefan").getBytes())));

		try {
			CloseableHttpResponse result = httpClient.execute(request);
			log.debug(result.getStatusLine().getStatusCode() + " " + result.getStatusLine().toString());
			String xml = EntityUtils.toString(result.getEntity());
			return xml;
		} catch (Exception e) {
			log.error("An error occurred when attempting to retrieve content from escenic at location: " + location);
			throw new FailedToRetrieveEscenicContentException("An error occurred when attempting to retrieve content from escenic at location: " + location + " due to : " + e);
		} finally {
			request.releaseConnection();
		}
	}


	public String retrieveEscenicItem(String location) throws FailedToRetrieveEscenicContentException {
		HttpGet request = new HttpGet(location);
		request.setHeader(generateAuthenticationHeader(escenicConfig.getUsername(), escenicConfig.getPassword()));
		try {
			CloseableHttpResponse result = httpClient.execute(request);
			log.debug(result.getStatusLine().getStatusCode() + " " + result.getStatusLine().toString());
			String xml = null;
			if (result.getEntity() != null) {
				xml = EntityUtils.toString(result.getEntity());
			}

			return xml;
		} catch (Exception e) {
			log.error("An error occurred when attempting to retrieve content from escenic at location: " + location);
			throw new FailedToRetrieveEscenicContentException("An error occurred when attempting to retrieve content from escenic at location: " + location + " due to : " + e);
		} finally {
			request.releaseConnection();
		}
	}

	protected Entry generateExistingEscenicEntry(String location) throws FailedToRetrieveEscenicContentException, FailedToDeserializeContentException {
		Entry entry = null;
		if (StringUtils.isNotEmpty(location)) {
			String xml = retrieveEscenicItem(location);
			log.debug("Result on GET on location : " + location + " from escenic:\n" + xml);
			entry = deserializeXml(xml);
		}
		return entry;
	}


	private boolean isValueEscaped(String fieldName) {

		if (StringUtils.isNotEmpty(fieldName)) {
			switch (fieldName) {
				case "body":
				case "embedCode":
				case "autocrop":
				case "summary":
					return true;
				default:
					return false;
			}
		}
		return false;
	}

	protected Value createValue(String fieldName, Object value) {
		if (value != null) {
			if (value instanceof String) {
				if (!isValueEscaped(fieldName)) {
					value = escapeHtml(value.toString());
				}
			}
			return new Value(Arrays.asList(new Object[]{value}));
		} else {
			return null;
		}
	}

	protected Field createField(String fieldName, Object value, List<Field> fields, com.atex.onecms.app.dam.integration.camel.component.escenic.model.List list) {
		return new Field(fieldName, createValue(fieldName, value), fields, list);
	}

	protected String wrapWithDiv(String text) {
		return "<div xmlns=\"http://www.w3.org/1999/xhtml\">" + text + "</div>";
	}

	protected String wrapWithCDATA(String text) {
		return "<![CDATA[" + text + "]]>";
	}

	protected String convertStructuredTextToEscenic(String structuredText, List<EscenicContent> escenicContentList) {
		log.debug("Body before replacing embeds: " + structuredText);
		structuredText = wrapWithDiv(structuredText);
		if (escenicContentList != null && !escenicContentList.isEmpty()) {
			structuredText = EscenicSocialEmbedProcessor.getInstance().replaceEmbeds(structuredText, escenicContentList);
		}
		log.debug("Body after replacing embeds: " + structuredText);
		return structuredText;
	}

	protected String getStructuredText(StructuredText str) {
		if (str != null)
			return str.getText();
		else
			return "";
	}

	//remove html tags and replace non breaking spaces
	protected String removeHtmlTags(String text) {
		return Jsoup.parse(text.replaceAll("&nbsp;", " ")).text();

	}

	protected static StringEntity generateAtomEntity(String xmlContent) {
		StringEntity entity = new StringEntity(xmlContent, StandardCharsets.UTF_8);
		entity.setContentType(APP_ATOM_XML);
		return entity;
	}

	protected InputStreamEntity generateImageEntity(InputStream in, String imgExt) {
		String mimeType = MimeUtil.getMimeType(imgExt).orElse(null);
		final ContentType contentType = ContentType.create(
			Optional.ofNullable(mimeType)
				.orElse(ContentType.APPLICATION_OCTET_STREAM.getMimeType()));
		final InputStreamEntity inputStreamEntity = new InputStreamEntity(in, contentType);
		return inputStreamEntity;
	}

	protected Header generateContentTypeHeader(String contentType) {
		return new BasicHeader(HttpHeaders.CONTENT_TYPE, contentType);
	}

	public Header generateAuthenticationHeader(String username, String password) throws RuntimeException {
//		String username = escenicConfig.getUsername();
//		String password = escenicConfig.getPassword();

		if (StringUtils.isEmpty(username) && StringUtils.isEmpty(password)) {
			throw new RuntimeException("Unable to access username & password for escenic");
		}

		String encoding = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

		return new BasicHeader(HttpHeaders.AUTHORIZATION, AUTH_PREFIX + encoding);
	}

	protected CloseableHttpResponse sendNewContentToEscenic(String xmlContent, String sectionId) throws FailedToSendContentToEscenicException {

		if (StringUtils.isBlank(sectionId)) {
			throw new RuntimeException("Unable to send content to escenic due to blank section id");
		}

		if (StringUtils.isBlank(escenicConfig.getApiUrl())) {
			throw new RuntimeException("Blank or missing escenic ApiUrl");
		}

		String url = escenicConfig.getApiUrl() + "/webservice/escenic/section/" + sectionId + "/content-items";

		HttpPost request = new HttpPost(url);
		request.setEntity(generateAtomEntity(xmlContent));
		request.expectContinue();
		request.setHeader(generateAuthenticationHeader(escenicConfig.getUsername(), escenicConfig.getPassword()));
		request.setHeader(generateContentTypeHeader(APP_ATOM_XML));

		log.debug("Sending the following xml to escenic:\n" + xmlContent);

		try {
			CloseableHttpResponse result = httpClient.execute(request);
			logXmlContentIfFailure(result.getStatusLine().getStatusCode(), xmlContent);
			return result;
		} catch (Exception e) {
			throw new FailedToSendContentToEscenicException("Failed to send new content to escenic: " + e);
		} finally {
			request.releaseConnection();
		}
	}

	protected void logXmlContentIfFailure(int statusCode, String xmlContent) {
		if (statusCode != HttpStatus.SC_CREATED && statusCode != HttpStatus.SC_OK && statusCode !=HttpStatus.SC_NO_CONTENT) {
			log.error("Failed to send the following xml:\n" + xmlContent);
		}
	}

	protected CloseableHttpResponse sendUpdatedContentToEscenic(String url, String xmlContent) throws FailedToSendContentToEscenicException {
		HttpPut request = new HttpPut(url);
		request.setEntity(generateAtomEntity(xmlContent));
		request.expectContinue();
		request.setHeader(generateAuthenticationHeader(escenicConfig.getUsername(), escenicConfig.getPassword()));
		request.setHeader(generateContentTypeHeader(APP_ATOM_XML));
		request.setHeader(HttpHeaders.IF_MATCH, "*");
		log.debug("Sending the following xml to UPDATE content in escenic:\n" + xmlContent);

		try {
			CloseableHttpResponse result = httpClient.execute(request);
			logXmlContentIfFailure(result.getStatusLine().getStatusCode(), xmlContent);
			return result;
		} catch (Exception e) {
			throw new FailedToSendContentToEscenicException("Failed to send an update to escenic due to: " + e);
		} finally {
			request.releaseConnection();
		}
	}

	protected static Entry deserializeXml(String xml) throws FailedToDeserializeContentException {
		Entry entry = null;
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(Entry.class);
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			entry = (Entry) unmarshaller.unmarshal(new StringReader(xml));

		} catch (JAXBException e) {
			throw new FailedToDeserializeContentException("Failed to deserialize content: " + e);
		}

		return entry;
	}

	protected static String serializeXml(Object object) {
		StringWriter stringWriter = new StringWriter();
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(Entry.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
//			marshaller.setProperty(Marshaller.JAXB_ENCODING, "Unicode");
//			marshaller.setProperty(Marshaller.JAXB_ENCODING, "utf8");
			marshaller.setProperty(CharacterEscapeHandler.class.getName(), new CustomCharacterEscapeHandler());
			marshaller.marshal(object, new StreamResult(stringWriter));
			return stringWriter.getBuffer().toString();

		} catch (Exception e) {
			throw new RuntimeException("An error occurred during serialization");
		} finally {
			if (stringWriter.getBuffer() != null) {
				return stringWriter.getBuffer().toString();
			} else {
				return null;
			}
		}
	}

	protected Control generateControl(String draft, String stateText) {
		Control control = new Control();
		control.setDraft(draft);
		control.setState(generateState(stateText));
		return control;
	}

	protected List<State> generateState(String stateText) {
		State state = new State();
		state.setState(stateText);
		state.setName(stateText);
		return Arrays.asList(state);
	}

	private String clean(String component) {
		if (component == null) return null;
		return CharMatcher.ASCII.retainFrom(component);
	}

	public List<Link> generateLinks(EscenicContent escenicContent) {
		List<Link> links = new ArrayList<>();
		if (escenicContent != null) {

			if (escenicContent instanceof EscenicImage) {
				EscenicImage escenicImage = (EscenicImage) escenicContent;
				Link link = createLink(null, THUMBNAIL_RELATION_GROUP, escenicImage.getThumbnailUrl(), "image/png",
					null, null, null, null, null, null);

				if (link != null) {
					links.add(link);
				}

				if (escenicImage.isTopElement()) {
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", escenicImage.getTitle(), null, null));
					fields.add(createField("caption", escenicImage.getCaption(), null, null));
					Link topElementLink = createLink(fields, PICTURE_RELATIONS_GROUP, escenicImage.getThumbnailUrl(), EscenicImage.IMAGE_MODEL_CONTENT_SUMMARY,
						escenicImage.getEscenicLocation(), ATOM_APP_ENTRY_TYPE, RELATED, escenicImage.getEscenicId(),
						escenicImage.getTitle(), PUBLISHED_STATE);

					if (topElementLink != null) {
						links.add(topElementLink);
					}
				}

				if (escenicImage.isInlineElement()) {
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", escenicImage.getTitle(), null, null));
					fields.add(createField("caption", escenicImage.getCaption(), null, null));
					Link inlineElementLink = createLink(fields, INLINE_RELATIONS_GROUP, escenicImage.getThumbnailUrl(), escenicImage.IMAGE_MODEL_CONTENT_SUMMARY,
						escenicImage.getEscenicLocation(), ATOM_APP_ENTRY_TYPE, RELATED, escenicImage.getEscenicId(),
						escenicImage.getTitle(), PUBLISHED_STATE);

					if (inlineElementLink != null) {
						links.add(inlineElementLink);
					}

				}

			} else if (escenicContent instanceof EscenicGallery) {
				EscenicGallery escenicGallery = (EscenicGallery) escenicContent;
				Link link = createLink(null, THUMBNAIL_RELATION_GROUP, escenicGallery.getThumbnailUrl(), "image/png",
					null, null, null, null, null, null);

				if (link != null) {
					links.add(link);
				}

				if (escenicGallery.isTopElement()) {
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", escenicGallery.getTitle(), null, null));
					Link topElementLink = createLink(fields, PICTURE_RELATIONS_GROUP, escenicGallery.getThumbnailUrl(), EscenicGallery.GALLERY_MODEL_CONTENT_SUMMARY,
						escenicGallery.getEscenicLocation(), ATOM_APP_ENTRY_TYPE, RELATED, escenicGallery.getEscenicId(),
						escenicGallery.getTitle(), PUBLISHED_STATE);
					if (topElementLink != null) {
						links.add(topElementLink);
					}
				}

				if (escenicContent.isInlineElement()) {
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", escenicGallery.getTitle(), null, null));
					Link inlineElementLink = createLink(fields, INLINE_RELATIONS_GROUP, escenicGallery.getThumbnailUrl(), EscenicGallery.GALLERY_MODEL_CONTENT_SUMMARY,
						escenicGallery.getEscenicLocation(), ATOM_APP_ENTRY_TYPE, RELATED, escenicGallery.getEscenicId(),
						escenicGallery.getTitle(), PUBLISHED_STATE);

					if (inlineElementLink != null) {
						links.add(inlineElementLink);
					}
				}

			} else if (escenicContent instanceof EscenicEmbed) {
				EscenicEmbed escenicEmbed = (EscenicEmbed) escenicContent;
				List<Field> fields = new ArrayList<>();
				fields.add(createField("title", escenicEmbed.getTitle(), null, null));
				Link link = createLink(fields, INLINE_RELATIONS_GROUP, null, EscenicEmbed.EMBED_MODEL_CONTENT_SUMMARY,
					escenicEmbed.getEscenicLocation(), ATOM_APP_ENTRY_TYPE, RELATED, escenicEmbed.getEscenicId(),
					escenicEmbed.getTitle(), PUBLISHED_STATE);

				if (link != null) {
					links.add(link);
				}
			}
		}

		return links;

	}

	protected Link createLink(List<Field> fields, String group, String thumbnail, String model, String href, String type, String rel, String identifier, String title, String state) {
		Link link = new Link();
		Payload payload = new Payload();
		payload.setField(fields);
		payload.setModel(escenicConfig.getModelUrl() + model);

		if (!StringUtils.isBlank(thumbnail)) {
			link.setThumbnail(thumbnail);
		}

		link.setGroup(group);
		link.setHref(href);
		link.setType(type);
		link.setRel(rel);
		link.setState(state);
		link.setPayload(payload);
		link.setIdentifier(identifier);
		link.setTitle(title);
		return link;
	}

	protected static String escapeHtml(String text) {
		return StringEscapeUtils.escapeHtml(text);
	}

	protected static Object extractContentBean(ContentResult contentCr) {
		if (contentCr != null) {
			com.atex.onecms.content.Content content = contentCr.getContent();
			if (content != null) {
				Object contentData = content.getContentData();
				if (contentData != null) {
					return contentData;
				}
			}
		} else {
			//throw an error if not already thrown?
		}
		return null;
	}

	protected static ContentResult checkAndExtractContentResult(ContentId contentId, ContentManager contentManager) throws RuntimeException {

		ContentResult<OneContentBean> contentCr = null;
		ContentVersionId contentVersionId = null;
		try {
			contentVersionId = contentManager.resolve(contentId, Subject.NOBODY_CALLER);
		} catch (StorageException e) {
			throw new RuntimeException("error occurred when resolving content id: " + contentId + e);
		}

		if (contentVersionId != null) {
			contentCr = contentManager.get(contentVersionId, null, OneContentBean.class, null, Subject.NOBODY_CALLER);
		} else {
			throw new RuntimeException("ContentVersionId not found");
		}

		if (contentCr != null && contentCr.getStatus().isSuccess()) {
			return contentCr;
		} else {
			throw new RuntimeException("Retrieing content failed: " + contentCr.getStatus());
		}
	}

	protected String getEscenicIdFromEngagement(EngagementDesc engagementDesc, String existingId) {
		String escenicId = existingId;
		if (engagementDesc != null) {

			if (StringUtils.isNotBlank(engagementDesc.getAppPk())) {
				escenicId = engagementDesc.getAppPk();
			}
		}

		return escenicId;
	}

	protected String getEscenicLocationFromEngagement(EngagementDesc engagementDesc, String existingLocation) {
		String escenicLocation = existingLocation;
		if (engagementDesc != null) {
			if (engagementDesc.getAttributes() != null) {
				for (EngagementElement element : engagementDesc.getAttributes()) {
					if (element != null) {
						if (StringUtils.equalsIgnoreCase(element.getName(), "location")) {
							escenicLocation = element.getValue();
						}
					}
				}
			}
		}
		return escenicLocation;
	}

	public List<Link> mergeLinks(List<Link> existingLinks, List<Link> links) {
		if (existingLinks != null && links != null) {
			for (Link existinglink : existingLinks) {

				boolean found = false;

				for (Link link : links) {
					if (link.equals(existinglink)) {
						found = true;
					}
				}

				if (!found && !StringUtils.equalsIgnoreCase(existinglink.getRel(), "related")) {
					links.add(existinglink);

				}
			}
			return links;
		}
		return existingLinks;
	}
}
