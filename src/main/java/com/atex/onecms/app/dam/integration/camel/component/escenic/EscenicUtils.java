package com.atex.onecms.app.dam.integration.camel.component.escenic;

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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * @author jakub
 */
public class EscenicUtils {

	private static String AUTH_PREFIX = "Basic ";
	private static final CustomEmbedParser customEmbedParser = new CustomEmbedParser();
	private static Logger log = LoggerFactory.getLogger(EscenicUtils.class);
	protected EscenicConfig escenicConfig;
	private static final String INLINE_RELATIONS_GROUP = "com.escenic.inlineRelations";
	private static final String PICTURE_RELATIONS_GROUP = "pictureRel";
	private static final String THUMBNAIL_RELATION_GROUP = "thumbnail";
	private static final String APP_ATOM_XML = "application/atom+xml";
	private static final String ATOM_APP_ENTRY_TYPE = APP_ATOM_XML + "; type=entry";
	private static final String RELATED = "related";
	private static final String PUBLISHED_STATE = "published";

	public CloseableHttpClient getHttpClient() {
		return httpClient;
	}

	protected CloseableHttpClient httpClient;
	static final int TIMEOUT = 6000;
	RequestConfig config = RequestConfig.custom()
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


	public String retrieveEscenicItem(String location) throws FailedToRetrieveEscenicContentException {
		HttpGet request = new HttpGet(location);
//		CloseableHttpClient httpClient = HttpClients.createDefault();
		request.setHeader(generateAuthenticationHeader());
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
			System.out.println(xml);
			log.debug("GETTING CONTENT: " + location + " FROM ESCENIC:\n" + xml);
			entry = deserializeXml(xml);
		}
		return entry;
	}


	protected static Value createValue(String fieldName, Object value) {
		if (value != null) {
			if (value instanceof String) {
				if (!StringUtils.equalsIgnoreCase(fieldName, "body") && !StringUtils.equalsIgnoreCase(fieldName, "embedCode") && !StringUtils.equalsIgnoreCase(fieldName, "autocrop")) {
					value = escapeHtml(value.toString());
				}
			}
			return new Value(Arrays.asList(new Object[]{value}));
		} else {
			return null;
		}

	}

	protected static Field createField(String fieldName, Object value, List<Field> fields, com.atex.onecms.app.dam.integration.camel.component.escenic.model.List list) {
		return new Field(fieldName, createValue(fieldName, value), fields, list);
	}


	protected String wrapWithDiv(String text) {
		return "<div xmlns=\"http://www.w3.org/1999/xhtml\">" + text + "</div>";
	}

	protected String wrapWithCDATA(String text) {
		return "<![CDATA[" + text + "]]>";
	}

	//TODO in here we need to escape < > & (and other chars that need to be escaped for xml within html elements)
	protected String convertStructuredTextToEscenic(String structuredText, List<EscenicContent> escenicContentList) {
		System.out.println("Before replacing embeds:\n" + structuredText);
		//todo just removed 2 lines below see if it works..
//		structuredText = StringEscapeUtils.unescapeHtml(structuredText);
//		org.apache.commons.lang3.StringEscapeUtils.unescapeXml(structuredText);
		System.out.println("After escaping html/xml :\n" + structuredText);
//		structuredText = structuredText.replaceAll("\u00a0", "");
//		structuredText = "<div xmlns=\"http://www.w3.org/1999/xhtml\">" + structuredText + "</div>";
		structuredText = wrapWithDiv(structuredText);
		String result = EscenicSocialEmbedProcessor.getInstance().replaceEmbeds(structuredText, escenicContentList);
		System.out.println("After replacing embeds:\n" + result);
		System.out.println("After replacing embeds:\n" + result);
		return result;
	}

	protected String getStructuredText(StructuredText str) {
		if (str != null)
			return str.getText();
		else
			return "";
	}

	protected static StringEntity generateAtomEntity(String xmlContent) {
		StringEntity entity = new StringEntity(xmlContent, StandardCharsets.UTF_8);
		entity.setContentType(APP_ATOM_XML);
		return entity;
	}

	protected InputStreamEntity generateImageEntity(InputStream in, String imgExt) {
//		ByteArrayEntity entity = new ByteArrayEntity(baos.toByteArray());
		String mimeType = MimeUtil.getMimeType(imgExt).orElse(null);
		final ContentType contentType = ContentType.create(
			Optional.ofNullable(mimeType)
				.orElse(ContentType.APPLICATION_OCTET_STREAM.getMimeType()));
		final InputStreamEntity inputStreamEntity = new InputStreamEntity(in, contentType);
//		entity.setContentType("image/" + imgExt);
		return inputStreamEntity;
	}

	protected Header generateContentTypeHeader(String contentType) {
		return new BasicHeader(HttpHeaders.CONTENT_TYPE, contentType);
	}

	protected Header generateAuthenticationHeader() {
		String username = escenicConfig.getUsername();
		String password = escenicConfig.getPassword();

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
		request.setHeader(generateAuthenticationHeader());
		request.setHeader(generateContentTypeHeader(APP_ATOM_XML));

		System.out.println("Sending the following xml to escenic:" + xmlContent);

		try {
			CloseableHttpResponse result = httpClient.execute(request);
			//todo here add better handling of response
			System.out.println(result.getStatusLine());
			System.out.println(EntityUtils.toString(result.getEntity()));
			//todo probably check the status code?
			return result;
		} catch (Exception e) {
			throw new FailedToSendContentToEscenicException("Failed to send new content to escenic: " + e);
		} finally {
			request.releaseConnection();
		}
	}

	protected CloseableHttpResponse sendUpdatedContentToEscenic(String url, String xmlContent) {
		HttpPut request = new HttpPut(url);

		//TODO timeout handlers - create a single static client
//		CloseableHttpClient httpClient = HttpClients.createDefault();

		request.setEntity(generateAtomEntity(xmlContent));
		request.expectContinue();
		request.setHeader(generateAuthenticationHeader());
		request.setHeader(generateContentTypeHeader(APP_ATOM_XML));
		//TODO we might need to do additonal querying here if we were to use ETAG ...
		request.setHeader(HttpHeaders.IF_MATCH, "*");
		System.out.println("Sending the following UPDATE xml to escenic:" + xmlContent);

		try {
			CloseableHttpResponse result = httpClient.execute(request);
//			System.out.println("result: " + EntityUtils.toString(result.getEntity()));
			System.out.println(result.getStatusLine());
			//todo probably check the status code?
			return result;
		} catch (Exception e) {
			System.out.println("Error - :" + e.getMessage());
			System.out.println("Error - :" + e.getCause());
		} finally {
			request.releaseConnection();
		}
		return null;

	}

	protected static Entry deserializeXml(String xml) throws FailedToDeserializeContentException {
		Entry entry = null;
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(Entry.class);
			System.out.println("jaxbContext is=" + jaxbContext.toString());
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
			//marshaller.marshal(object, new OutputStreamWriter(System.out));

			marshaller.marshal(object, new StreamResult(stringWriter));
			return stringWriter.getBuffer().toString();

		} catch (Exception e) {
			log.error("An error occurred during serialization");
			e.printStackTrace();
		} finally {
			if (stringWriter.getBuffer() != null) {
				return stringWriter.getBuffer().toString();
			} else {
				return null;
			}
		}
	}


	private String clean(String component) {
		if (component == null) return null;
		return CharMatcher.ASCII.retainFrom(component);
	}

	public List<Link> generateLinks(EscenicContent escenicContent ) {
		List<Link> links = new ArrayList<>();

		//TODO we need to be able to map things correctly here!!
		if (escenicContent != null) {

			if (escenicContent instanceof EscenicImage) {
				EscenicImage escenicImage = (EscenicImage) escenicContent;


//				Link thumbnailLink = new Link();
//				thumbnailLink.setHref(escenicImage.getThumbnailUrl());
//				thumbnailLink.setRel("thumbnail");
//				thumbnailLink.setType("image/png");
//				links.add(thumbnailLink);
				Link link = createLink(null, THUMBNAIL_RELATION_GROUP, escenicImage.getThumbnailUrl(), "image/png",
					null, null, null, null, null, null);

				if (link != null) {
					links.add(link);
				}


				if (escenicImage.isTopElement()) {
					//TODO replace with createLink
					//TODO does this happen automatically? Do we define pictureRel along side the inline relations?
//					Link pictureRelLink = new Link();
//					Payload payload1 = new Payload();
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", escenicImage.getTitle(), null, null));
					//TODO
					fields.add(createField("caption", "", null, null));
//					payload1.setField(fields);
//					payload1.setModel(escenicConfig.getModelUrl() + EscenicImage.IMAGE_MODEL_CONTENT_SUMMARY);
//					pictureRelLink.setPayload(payload1);
//					pictureRelLink.setGroup("pictureRel");
//					pictureRelLink.setHref(escenicContent.getEscenicLocation());
//					pictureRelLink.setThumbnail(escenicImage.getThumbnailUrl());
//					pictureRelLink.setRel("related");
//					pictureRelLink.setState("published");
//					pictureRelLink.setType("application/atom+xml; type=entry");
//					pictureRelLink.setIdentifier(escenicContent.getEscenicId());
//					links.add(pictureRelLink);

					Link topElementLink = createLink(fields, PICTURE_RELATIONS_GROUP, escenicImage.getThumbnailUrl(), EscenicImage.IMAGE_MODEL_CONTENT_SUMMARY,
						escenicImage.getEscenicLocation(), ATOM_APP_ENTRY_TYPE, RELATED, escenicImage.getEscenicId(),
						escenicImage.getTitle(), PUBLISHED_STATE);

					if (topElementLink != null) {
						links.add(topElementLink);
					}
				}

				if (escenicImage.isInlineElement()) {
//					Link link = new Link();
					//TODO replace with createLink
//					Payload payload = new Payload();
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", escenicImage.getTitle(), null, null));
					fields.add(createField("caption", escenicImage.getTitle(), null, null));
//					link.setGroup("com.escenic.inlineRelations");
//					link.setThumbnail(escenicImage.getThumbnailUrl());
//					payload.setField(fields);
//					payload.setModel(escenicConfig.getModelUrl() + EscenicImage.IMAGE_MODEL_CONTENT_SUMMARY);
//					link.setHref(escenicContent.getEscenicLocation());
//					link.setType("application/atom+xml; type=entry");
//					link.setRel("related");
//					link.setState("published");
//					link.setPayload(payload);
//					link.setIdentifier(escenicContent.getEscenicId());
//
//					link.setTitle(escenicImage.getTitle());
//					links.add(link);
					Link inlineElementLink = createLink(fields, INLINE_RELATIONS_GROUP, escenicImage.getThumbnailUrl(), escenicImage.IMAGE_MODEL_CONTENT_SUMMARY,
						escenicImage.getEscenicLocation(), ATOM_APP_ENTRY_TYPE, RELATED, escenicImage.getEscenicId(),
						escenicImage.getTitle(), PUBLISHED_STATE);

					if (inlineElementLink != null) {
						links.add(inlineElementLink);
					}

				}

			} else if (escenicContent instanceof EscenicGallery) {
				EscenicGallery escenicGallery = (EscenicGallery) escenicContent;
//				Link thumbnailLink = new Link();
//				thumbnailLink.setHref(escenicGallery.getThumbnailUrl());
//				thumbnailLink.setRel("thumbnail");
//				thumbnailLink.setType("image/png");
//				links.add(thumbnailLink);


				Link link = createLink(null, THUMBNAIL_RELATION_GROUP, escenicGallery.getThumbnailUrl(), "image/png",
					null, null, null, null, null, null);

				if (link != null) {
					links.add(link);
				}


				if (escenicGallery.isTopElement()) {
					//TODO does this happen automatically? Do we define pictureRel along side the inline relations?
					//TODO replace with createLink
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", escenicGallery.getTitle(), null, null));
					fields.add(createField("caption", escenicGallery.getTitle(), null, null));
//					payload1.setField(fields1);
//					payload1.setModel(escenicConfig.getModelUrl() + EscenicGallery.GALLERY_MODEL_CONTENT_SUMMARY);
//					pictureRelLink.setPayload(payload1);
//					pictureRelLink.setGroup("pictureRel");
//					pictureRelLink.setHref(escenicContent.getEscenicLocation());
//					pictureRelLink.setThumbnail(escenicGallery.getThumbnailUrl());
//					pictureRelLink.setRel("related");
//					pictureRelLink.setState("published");
//					pictureRelLink.setType("application/atom+xml; type=entry");
//					pictureRelLink.setIdentifier(escenicGallery.getEscenicId());
//					links.add(pictureRelLink);

					Link topElementLink = createLink(fields, PICTURE_RELATIONS_GROUP, escenicGallery.getThumbnailUrl(), EscenicGallery.GALLERY_MODEL_CONTENT_SUMMARY,
						escenicGallery.getEscenicLocation(), ATOM_APP_ENTRY_TYPE, RELATED, escenicGallery.getEscenicId(),
						escenicGallery.getTitle(), PUBLISHED_STATE);

					if (topElementLink != null) {
						links.add(topElementLink);
					}
				}



				if (escenicContent.isInlineElement()) {



//					Link link = new Link();
//					//TODO replace with createLink
//					Payload payload = new Payload();
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", escenicGallery.getTitle(), null, null));
					fields.add(createField("caption", "test", null, null));
//					link.setGroup("com.escenic.inlineRelations");
//					link.setThumbnail(escenicGallery.getThumbnailUrl());
//					payload.setField(fields);
//					payload.setModel(escenicConfig.getModelUrl() + EscenicGallery.GALLERY_MODEL_CONTENT_SUMMARY);
//					link.setHref(escenicContent.getEscenicLocation());
//					link.setType("application/atom+xml; type=entry");
//					link.setRel("related");
//					link.setState("published");
//					link.setPayload(payload);
//					link.setIdentifier(escenicContent.getEscenicId());
//
//					link.setTitle(escenicGallery.getTitle());
//					links.add(link);

					Link inlineElementLink = createLink(fields, INLINE_RELATIONS_GROUP, escenicGallery.getThumbnailUrl(), EscenicGallery.GALLERY_MODEL_CONTENT_SUMMARY,
						escenicGallery.getEscenicLocation(), ATOM_APP_ENTRY_TYPE, RELATED, escenicGallery.getEscenicId(),
						escenicGallery.getTitle(), PUBLISHED_STATE);

					if (inlineElementLink != null) {
						links.add(inlineElementLink);
					}
				}

			} else if (escenicContent instanceof EscenicEmbed) {
				EscenicEmbed escenicEmbed = (EscenicEmbed) escenicContent;
				//TODO replace with createLink

				List<Field> fields = new ArrayList<>();
				fields.add(createField("title", "adm embed", null, null));
				//todo change title? what do we set it to?
				Link link = createLink(fields, INLINE_RELATIONS_GROUP, null, EscenicEmbed.EMBED_MODEL_CONTENT_SUMMARY,
						   escenicEmbed.getEscenicLocation(), ATOM_APP_ENTRY_TYPE, RELATED, escenicEmbed.getEscenicId(),
					  "TITLE", PUBLISHED_STATE);

				if (link != null) {
					links.add(link);
				}

//				Link link = new Link();
//				Payload payload = new Payload();
//
//				link.setGroup("com.escenic.inlineRelations");
//				payload.setField(fields);
//				payload.setModel(escenicConfig.getModelUrl() + EscenicEmbed.EMBED_MODEL_CONTENT_SUMMARY);
//				link.setHref(escenicEmbed.getEscenicLocation());
//				link.setType("application/atom+xml; type=entry");
//				link.setRel("related");
//				link.setState("published");
//				link.setPayload(payload);
//				link.setIdentifier(escenicEmbed.getEscenicId());
//
//				link.setTitle("adm embed");
//				links.add(link);
			}
		}

		return links;

	}

	private Link createLink(List<Field> fields, String group, String thumbnail, String model, String href, String type, String rel, String identifier, String title, String state){
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

	private String cleanHTMLToText(String articleField) {

		if (articleField == null) return null;

		Document doc = Jsoup.parse(articleField);

		doc = new Cleaner(Whitelist.none()).clean(doc);

		return doc.body().text();

	}

	private String cleanHTMLToXHTML(String text) {
		if (text == null) return null;

		Document doc = Jsoup.parse(text);

		removeComments(doc);

		doc.outputSettings().escapeMode(Entities.EscapeMode.xhtml);

		return doc.body().html();
	}

	protected static String escapeHtml(String text) {
		return StringEscapeUtils.escapeHtml(text);
	}

	private void removeComments(Node node) {
		for (int i = 0; i < node.childNodes().size(); ) {
			Node child = node.childNode(i);
			if (child.nodeName().equals("#comment")) child.remove();
			else {
				removeComments(child);
				i++;
			}
		}
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

	protected static ContentResult checkAndExtractContentResult(ContentId contentId, ContentManager contentManager) throws RuntimeException  {

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
			//error here about contentversionid
			throw new RuntimeException("ContentVersionId not found");
		}

		if (contentCr != null && contentCr.getStatus().isSuccess()) {
			return contentCr;
		} else {
			//throw an error?
			throw new RuntimeException("Retrieing content failed: " + contentCr.getStatus());
		}
	}
}
