package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.gong.utils.WsErrorUtil;
import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.engagement.EngagementElement;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToDeserializeContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToRetrieveEscenicContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToSendContentToEscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.*;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Content;
import com.atex.onecms.app.dam.integration.camel.component.escenic.ws.EscenicResource;
import com.atex.onecms.app.dam.standard.aspects.*;
import com.atex.onecms.content.*;
import com.atex.onecms.content.repository.StorageException;
import com.atex.onecms.ws.service.ErrorResponseException;
import com.atex.plugins.structured.text.StructuredText;
import com.google.common.base.CharMatcher;

import com.sun.xml.bind.marshaller.CharacterEscapeHandler;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.bind.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author jakub
 */
public class EscenicUtils {

	private static String AUTH_PREFIX = "Basic ";
	private static final java.util.logging.Logger LOGGER = Logger.getLogger(EscenicUtils.class.getName());
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
		LOGGER.setLevel(Level.FINEST);
	}

	//TODO - REMOVE LATER - temporarily load from content rather than through web service call
	public String retrieveSectionListFromContent(ContentManager contentManager) throws ErrorResponseException {
		final ContentVersionId versionId = contentManager.resolve("escenic.section.mappings.list.configuration", Subject.NOBODY_CALLER);
		final ContentResult<ConfigurationDataBean> cr = contentManager.get(versionId, ConfigurationDataBean.class, Subject.NOBODY_CALLER);
		if (cr.getStatus().isError()) {
			throw WsErrorUtil.error("cannot get configuration", cr.getStatus());
		}
		final ConfigurationDataBean contentData = cr.getContent().getContentData();
		if (contentData.getJson() != null) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Retrieved section list:\n" + contentData.getJson());
			}
		}
		return contentData.getJson();
	}


	public String retrieveSectionList(String location) throws FailedToRetrieveEscenicContentException {
		if (StringUtils.isBlank(location)) {
			throw new RuntimeException("Unable to read section list url from config");
		}

		HttpGet request = new HttpGet(location);
		Header authHeader = generateAuthenticationHeader(escenicConfig.getSectionListUsername(), escenicConfig.getSectionListPassword());
		request.setHeader(authHeader);

		try {
			CloseableHttpResponse result = httpClient.execute(request);
			String xml = null;
			if (result.getEntity() != null) {
				xml = EntityUtils.toString(result.getEntity());
			}

			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Retrieved section list:\n" + xml);
			}
			return xml;
		} catch (Exception e) {
			LOGGER.severe("An error occurred when attempting to retrieve content from escenic at location: " + location);
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
			String xml = null;
			if (result.getEntity() != null) {
				xml = EntityUtils.toString(result.getEntity());
			}

			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Retrieved escenic item:\n" + xml);
			}
			return xml;
		} catch (Exception e) {
			LOGGER.severe("An error occurred when attempting to retrieve content from escenic at location: " + location);
			throw new FailedToRetrieveEscenicContentException("An error occurred when attempting to retrieve content from escenic at location: " + location + " due to : " + e);
		} finally {
			request.releaseConnection();
		}
	}

	protected Entry generateExistingEscenicEntry(String location) throws FailedToRetrieveEscenicContentException, FailedToDeserializeContentException {
		Entry entry = null;
		if (StringUtils.isNotEmpty(location)) {
			String xml = retrieveEscenicItem(location);
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Result on GET on location : " + location + " from escenic:\n" + xml);
			}
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
				case "title":
				case "headline":
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
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Body before replacing embeds: " + structuredText);
		}

		structuredText = wrapWithDiv(structuredText);
		if (escenicContentList != null && !escenicContentList.isEmpty()) {
			structuredText = EscenicSocialEmbedProcessor.getInstance().replaceEmbeds(structuredText, escenicContentList);
		} else {
			//still ensure it's parsed
			final org.jsoup.nodes.Document doc = Jsoup.parseBodyFragment(structuredText);
			doc.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);
//			doc.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
			structuredText = doc.body().html();
		}

		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Body after replacing embeds: " + structuredText);
		}
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
		if (StringUtils.isNotBlank(text)) {
			org.jsoup.nodes.Document d = Jsoup.parseBodyFragment(text);
			d.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);
			return d.body().text();
		}
		return text;

	}

	//remove html tags and replace non breaking spaces
	protected String replaceNonBreakingSpaces(String text) {
		if (StringUtils.isNotBlank(text)) {
			text = text.replaceAll("/\\\\u009/g", "");
			text = text.replaceAll("\t", "");

			return text.replaceAll("&nbsp;", " ");
		}
		return text;

	}

	protected static StringEntity generateAtomEntity(String xmlContent) {
		StringEntity entity = new StringEntity(xmlContent, StandardCharsets.UTF_8);
		entity.setContentType(APP_ATOM_XML);
		return entity;
	}

	protected InputStreamEntity generateImageEntity(InputStream in, String mimeType) {
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

		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Sending the following xml to escenic:\n" + xmlContent);
		}

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
			LOGGER.severe("Failed to send the following xml:\n" + xmlContent);
		}
	}

	protected CloseableHttpResponse sendUpdatedContentToEscenic(String url, String xmlContent) throws FailedToSendContentToEscenicException {
		HttpPut request = new HttpPut(url);
		request.setEntity(generateAtomEntity(xmlContent));
		request.expectContinue();
		request.setHeader(generateAuthenticationHeader(escenicConfig.getUsername(), escenicConfig.getPassword()));
		request.setHeader(generateContentTypeHeader(APP_ATOM_XML));
		request.setHeader(HttpHeaders.IF_MATCH, "*");

		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Sending the following xml to UPDATE content in escenic:\n" + xmlContent);
		}

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

	public static Entry deserializeXml(String xml) throws FailedToDeserializeContentException {
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
			} else if (escenicContent instanceof EscenicContentReference) {
				EscenicContentReference escenicContentReference = (EscenicContentReference) escenicContent;
				List<Field> fields = new ArrayList<>();
				fields.add(createField("title", escenicContentReference.getTitle(), null, null));

				Link link = createLink(fields, INLINE_RELATIONS_GROUP, null, "/content-summary/" + escenicContentReference.getType(),
					escenicContentReference.getEscenicLocation(), ATOM_APP_ENTRY_TYPE, RELATED, escenicContentReference.getEscenicId(),
					escenicContentReference.getTitle(), PUBLISHED_STATE);

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
		link.setTitle(escapeHtml(title));
		return link;
	}

	protected String escapeHtml(String text) {
		return StringEscapeUtils.escapeXml10(removeHtmlTags(text));
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
			throw new RuntimeException("Unable to access content result");
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

				if (!found && !StringUtils.equalsIgnoreCase(existinglink.getGroup(), "pictureRel") && !StringUtils.equalsIgnoreCase(existinglink.getGroup(), "com.escenic.inlineRelations")) {
					existinglink.setTitle(escapeHtml(existinglink.getTitle()));
					links.add(existinglink);
				}
			}
			return links;
		}
		return existingLinks;
	}

	public String extractIdFromLocation(String escenicLocation) {
		String id = null;
		if (StringUtils.isNotEmpty(escenicLocation)) {
			id = escenicLocation.substring(escenicLocation.lastIndexOf('/') + 1);
		}

		return id;
	}

	public String createSearchGroups(String xml) throws EscenicException {
		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			docFactory.setNamespaceAware(true);
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(new InputSource(new StringReader(xml)));
			Node feed = doc.getFirstChild();
			NodeList list  = feed.getChildNodes();
			Node currentGroupNode = null;
			for (int i=0; i < list.getLength(); i++) {
				Node node = list.item(i);
				if (node != null) {
					if (StringUtils.equalsIgnoreCase(node.getNodeName(), "facet:group")) {
						currentGroupNode = node;
					}

					if (StringUtils.equalsIgnoreCase(node.getNodeName(), "opensearch:query")) {
						if (currentGroupNode != null) {
							currentGroupNode.appendChild(node);
							currentGroupNode.appendChild(doc.createTextNode("\n"));
						}
					}
				}
			}

			StringWriter stringWriter = new StringWriter();
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.METHOD, "xml" );
			transformer.setOutputProperty(OutputKeys.INDENT, "false" );
			DOMSource source = new DOMSource(doc);
			transformer.transform(source,  new StreamResult(stringWriter));
			return stringWriter.getBuffer().toString();
		} catch (ParserConfigurationException | TransformerException | IOException | SAXException e) {
			throw new EscenicException("Failed to process the response to search query" + e);
		}
	}

	public void processSearchInfo(Feed feed) throws URISyntaxException {
		if (feed != null) {
			List<Link> links = feed.getLink();

			if (links != null) {
				for (Link link : links) {
					processPageInfo(feed, link);
				}
			}
		}
	}

	private void processPageInfo(Feed feed, Link link) throws URISyntaxException {
		if (link != null) {
			String rel = link.getRel();
			if (StringUtils.isNotBlank(link.getHref())) {
				List<NameValuePair> params = URLEncodedUtils.parse(new URI(link.getHref()), "UTF-8");
				if (params != null) {
					if (StringUtils.isNotBlank(rel)) {
						switch (rel.toLowerCase()) {
							case "self":
								for (NameValuePair param : params) {
									if (StringUtils.equalsIgnoreCase(param.getName(), "pw")) {
										feed.setSelfPageNumber(param.getValue());
									} else if (StringUtils.equalsIgnoreCase(param.getName(), "c")) {
										feed.setSelfItemCount(param.getValue());
									}
								}
								break;
							case "first":
								for (NameValuePair param : params) {
									if (StringUtils.equalsIgnoreCase(param.getName(), "pw")) {
										feed.setFirstPageNumber(param.getValue());
									} else if (StringUtils.equalsIgnoreCase(param.getName(), "c")) {
										feed.setFirstItemCount(param.getValue());
									}
								}
								break;
							case "next":
								for (NameValuePair param : params) {
									if (StringUtils.equalsIgnoreCase(param.getName(), "pw")) {
										feed.setNextPageNumber(param.getValue());
									} else if (StringUtils.equalsIgnoreCase(param.getName(), "c")) {
										feed.setNextItemCount(param.getValue());
									}
								}
								break;
							case "last":
								for (NameValuePair param : params) {
									if (StringUtils.equalsIgnoreCase(param.getName(), "pw")) {
										feed.setLastPageNumber(param.getValue());
									} else if (StringUtils.equalsIgnoreCase(param.getName(), "c")) {
										feed.setLastItemCount(param.getValue());
									}
								}
								break;
						}
					}
				}
			}
		}
	}

	public void processFacetInfo(Feed feed) {
		if (feed != null) {
			List<Group> filteredGroups = new ArrayList<Group>();
			if (feed.getGroup() != null) {
				for (Group group : feed.getGroup()) {
					if (group != null) {
						if (!StringUtils.equalsIgnoreCase(group.getTitle(), "creationdate") && !StringUtils.equalsIgnoreCase(group.getTitle(), "publishdate")) {
							Group facetGroup = new Group();
							facetGroup.setTitle(group.getTitle());
							List<Query> queries = new ArrayList<>();
							if (group.getQuery() != null) {
								for (Query query : group.getQuery()) {
									String[] relatedArray = query.getRelated().split(" ");
									if (relatedArray != null && relatedArray.length > 0) {
										query.setRelated(relatedArray[relatedArray.length - 1]);
									}
									queries.add(query);
								}
								facetGroup.setQuery(queries);
							}
							filteredGroups.add(facetGroup);
						} else {
							filteredGroups.add(group);
						}
					}
				}
			}

			if (!filteredGroups.isEmpty()) {
				feed.setGroup(filteredGroups);
			}
		}
	}

	public String getFirstBodyParagraph(String html) {
		if (StringUtils.isNotBlank(html)) {
			Element document = Jsoup.parse(html).body();
			if (document != null) {
				for (Element element : document.children()) {
					if (StringUtils.equalsIgnoreCase(element.nodeName(), "p")) {
						if (StringUtils.isNotBlank(element.text())) {

							return element.text();
						}
					}

				}
				Element element = document.select("p").first();
				if (element != null && StringUtils.isNotBlank(element.text())) {
					return element.text();
				}
			}

			return "";

		}

		return "";

	}

	public String removeFirstParagraph(String text) {
		org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(text);
		document.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);
		Element doc = document.body();

		if (doc != null) {
				for (Element element : doc.children()) {
					if (StringUtils.equalsIgnoreCase(element.nodeName(), "p")) {
						if (StringUtils.isNotBlank(element.text())) {
							element.remove();
							return document.body().html();
						}
					}
				}


				Element element = doc.select("p").first();
				if (element != null && StringUtils.isNotBlank(element.text())) {
					element.remove();
					return document.body().html();
				}
		}

		return text;
	}

	public boolean isUpdateAllowed(Entry entry) {
		if (entry != null) {
			try {
				Content content = entry.getContent();
				if (content != null) {
					Payload payload = content.getPayload();
					if (payload != null) {
						List<Field> fields = payload.getField();
						if (fields != null) {
							for (Field field : fields) {
								if (field != null) {
									if (StringUtils.equalsIgnoreCase(field.getName(), "allowCUEUpdates")) {
										if (field.getValue() != null && field.getValue().getValue() != null) {
												for (Object o : field.getValue().getValue()) {
													if (o instanceof String) {
														String flag = o.toString();
														return StringUtils.equalsIgnoreCase(flag, "false") ? true : false;
													}
												}
										}
									}
								}
							}
						}
					}
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to extract a value for allowCUEUpdates flag." + e);
			}
		}
		return false;
	}

	public Title createTitle(String value, String type) {
		Title title = new Title();
		title.setType(type);
		title.setTitle(escapeHtml(value));
		return title;
	}

	public Summary createSummary(String value, String type) {
		Summary summary = new Summary();
		summary.setType(type);
		summary.setSummary(escapeHtml(value));
		return summary;
	}


	public String getQuery(String query){
		if (StringUtils.isBlank(query)) {
			query = EscenicResource.DEFAULT_QUERY;
		} else {

			//process the query part here:
			String[] terms = query.split(" ");
			if (terms != null && terms.length > 1) {
				query = "((" + query + ")";
				for (int i=0; i < terms.length; i++){
					String s = terms[i];
					if (StringUtils.isNotBlank(s)) {
						if (i == 0) {
							query += " OR ((";
						} else {
							query += " (";
						}

						query += s + " OR " + s + "*)";

						if (terms.length-1 == i) {
							query += "))";
						}
					}
				}
			} else {
				query = "((" + query + ") OR (" + query + " OR " + query + "*))";
			}
		}
		return query;
	}

	public boolean isAlreadyProcessed(List<EscenicContent> list, CustomEmbedParser.SmartEmbed embed) {
		if (list != null && embed != null) {
			for (EscenicContent content : list) {
				if (content != null) {
					//special case for social embeds - instead of comparing the onecms id we'll be comparing the embed URL
					if (StringUtils.equalsIgnoreCase(embed.getObjType(), EscenicEmbed.SOCIAL_EMBED_TYPE) && content instanceof EscenicEmbed) {
						EscenicEmbed socialEmbed = (EscenicEmbed) content;
						if (socialEmbed != null) {
							if (StringUtils.isNotEmpty(socialEmbed.getEmbedUrl()) && StringUtils.isNotEmpty(embed.getEmbedUrl()) &&
								StringUtils.equalsIgnoreCase(socialEmbed.getEmbedUrl(), embed.getEmbedUrl())) {
								return true;
							}
						}
					} else if (content.getOnecmsContentId() != null) {
						if (embed.getContentId() != null && embed.getContentId().equals(content.getOnecmsContentId())) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	public Publication cleanUpPublication(Publication publication) {
		if (publication != null) {
			publication.setTitle(escapeHtml(publication.getTitle()));
			List<Link> links = publication.getLink();

			if (links != null) {
				for (Link link : links) {
					link.setTitle(escapeHtml(link.getTitle()));
				}
			}
		}
		return publication;
	}
}
