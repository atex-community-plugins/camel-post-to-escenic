package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.nosql.image.ImageContentDataBean;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToDeserializeContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToRetrieveEscenicContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.*;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Content;
import com.atex.onecms.app.dam.standard.aspects.*;
import com.atex.onecms.content.*;
import com.atex.onecms.content.aspects.Aspect;
import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.repository.StorageException;
import com.atex.onecms.image.CropInfo;
import com.atex.onecms.image.ImageEditInfoAspectBean;
import com.atex.onecms.image.ImageInfoAspectBean;
import com.atex.onecms.image.Rectangle;
import com.atex.onecms.ws.image.ImageServiceConfigurationProvider;
import com.atex.onecms.ws.image.ImageServiceUrlBuilder;
import com.atex.plugins.structured.text.StructuredText;
import com.google.common.base.CharMatcher;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.PolicyCMServer;
import com.sun.xml.bind.marshaller.CharacterEscapeHandler;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 *
 * @author jakub
 */
public class EscenicUtils {

	//TODO Q: will have to figure out the section and a number based on web section? The below will be stored in the config
	public static long MAX_IMAGE_SIZE = 2073600;
	private static String AUTH_PREFIX = "Basic ";
	protected static final String IMAGE_TYPE = "image";
	protected static final String GALLERY_TYPE = "gallery";
	protected static final String SOCIAL_EMBED_TYPE = "socialEmbed";

	private static final CustomEmbedParser customEmbedParser = new CustomEmbedParser();
	private static Logger log = LoggerFactory.getLogger(EscenicUtils.class);

	public EscenicUtils() {

	}

	public static String retrieveEscenicItem(String location, EscenicConfig escenicConfig) throws FailedToRetrieveEscenicContentException {
		//todo read the value ..

		HttpGet request = new HttpGet(location);
		CloseableHttpClient httpClient = HttpClients.createDefault();
		request.setHeader(generateAuthenticationHeader(escenicConfig));
		try {
			CloseableHttpResponse result = httpClient.execute(request);
			System.out.println(result.getStatusLine());
			//todo probably check the status code?
			String xml = EntityUtils.toString(result.getEntity());
			return xml;
		} catch (ParseException | IOException e) {
			log.error("An error occurred when attempting to retrieve content from escenic at location: " + location);
			throw new FailedToRetrieveEscenicContentException("An error occurred when attempting to retrieve content from escenic at location: " + location + " due to : " + e);
		} finally {
			request.releaseConnection();
		}
	}

	public static List<CustomEmbedParser.SmartEmbed> processEmbeds(String text){
		return customEmbedParser.getSmartEmbed(text);
	}

	public String convert(EscenicConfig escenicConfig, Entry existingEntry, OneArticleBean article,
						  final ContentResult source, String location, List<EscenicContent> escenicContentList)
		throws CallbackException {


		if (existingEntry != null) {
			updateIdsForEscenicContent(existingEntry, escenicContentList);
		}

		return processArticle(article, source, existingEntry, escenicContentList);
			//source/data/aspects to get all aspects
	}

	private void updateIdsForEscenicContent(Entry entry, List<EscenicContent> escenicContentList) {

		if (escenicContentList != null) {
			for (EscenicContent escenicContent : escenicContentList) {
				if (escenicContent != null) {
					for(Field field : entry.getContent().getPayload().getField()) {

						if (field != null && StringUtils.equalsIgnoreCase(field.getName(), "body")) {

							if (field.getValue() != null && field.getValue().getValue() != null) {
								for ( Object o : field.getValue().getValue()){

										List<Element> elements = customEmbedParser.processBodyFromEscenic(o.toString());

										for (Element e : elements) {
											if (StringUtils.isNotEmpty(escenicContent.getEscenicLocation()) &&
												(e.hasAttr("href") && StringUtils.equalsIgnoreCase(escenicContent.getEscenicLocation(), e.attr("href")) ||
													(e.hasAttr("src") &&
														StringUtils.equalsIgnoreCase(escenicContent.getEscenicLocation().replaceAll("escenic/content", "thumbnail/article"), e.attr("src"))))){
												if (e.hasAttr("id") && !e.attr("id").startsWith("_") && (escenicContent.getId() == null || !escenicContent.getId().startsWith("_"))) {
													escenicContent.setId("_" + e.attr("id"));
												} else if (e.hasAttr("id") && e.attr("id").startsWith("_") && (escenicContent.getId() == null || !escenicContent.getId().startsWith("_"))) {
													escenicContent.setId(e.attr("id"));
												}
											}
										}
								}
							} else {
								System.out.println("The value was null!!!");
							}
						}
					}
				}
			}
		}
	}

	protected Entry generateExistingEscenicEntry(String location, EscenicConfig escenicConfig) throws FailedToRetrieveEscenicContentException, FailedToDeserializeContentException {
		Entry entry = null;
		if (StringUtils.isNotEmpty(location)) {

			String xml = retrieveEscenicItem(location, escenicConfig);
			System.out.println(xml);

			try {
				entry = deserializeXml(xml);
			} catch (JAXBException e) {
				throw new FailedToDeserializeContentException("Failed to deserialize content: " + e);
			}
		}

		return entry;
	}


	//TODO differentiate between an update and a brand new content
	private String processArticle(OneArticleBean article, ContentResult source, Entry existingEntry, List<EscenicContent> escenicContentList) {
		Entry entry = new Entry();
	    Title title = new Title();
		title.setType("text");
		title.setTitle(escapeHtml(article.getName()));
		Payload payload = new Payload();
		Content content = new Content();
		Control control = new Control();
		control.setDraft("no");
		State state = new State();
		state.setState("published");
		state.setName("published");
//		state.setHref("http://inm-test-editorial.inm.lan:8081/webservice/escenic/content/state/published/editor");
//		List<State> states = new ArrayList<>();
//		states.add(state);
		control.setState(Arrays.asList(state));

//		List<Link> links = generateLinksForArticle(escenicContentList);

		//TODO how do we handle updates? we need to somehow merge existing links with the links we generate here...
		//todo we currently reuse whatever is in escenic - thing to consider is what if we want to remove a relationship? or replace it?
//		entry.setLink(links);

		List<Field> fields = generateArticleFields(article, source, escenicContentList, existingEntry);
		payload.setField(fields);
		//TODO model will depend on the type of content we're working with
		//method here to detect content type and set different model based on that?
		//- in this case it will always be an article ?
		payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-type/news");
		content.setPayload(payload);
		content.setType("application/vnd.vizrt.payload+xml");
		entry.setContent(content);
		entry.setControl(control);


		List<Link> links = new ArrayList<>();
		for (EscenicContent escenicContent : escenicContentList) {
			if (escenicContent != null) {
				links.addAll(escenicContent.getLinks());
			}
		}
		entry.setLink(links);

		if (existingEntry != null) {
			entry = processExistingArticle(existingEntry, entry, escenicContentList);
//			entry.setLink(existingEntry.getLink());
		}

		return serializeXml(entry);
	}

	private Entry processExistingArticle(Entry existingEntry, Entry entry, List<EscenicContent> escenicContentList) {
		List<Link> existingLinks = existingEntry.getLink();
		List<Link> links = entry.getLink();

		List<Field> existingFields = existingEntry.getContent().getPayload().getField();
		List<Field> newFields = entry.getContent().getPayload().getField();
		for (Field field : existingFields) {
			for (Field newField : newFields) {
				if (StringUtils.equalsIgnoreCase(field.getName(), newField.getName())) {
					field.setValue(newField.getValue());
				}
			}
		}

		existingEntry.setLink(entry.getLink());
		existingEntry.setControl(entry.getControl());

		if (existingLinks != null && links != null) {
			for (Link existinglink : existingLinks) {

				boolean found = false;

				for (Link link : links) {

					if (StringUtils.equalsIgnoreCase(existinglink.getHref(), link.getHref()) &&
						StringUtils.equalsIgnoreCase(existinglink.getIdentifier(), link.getIdentifier()) &&
						StringUtils.equalsIgnoreCase(existinglink.getType(), link.getType())) {

						//it's the same link...
						found = true;
					}
				}

				if (!found && !StringUtils.equalsIgnoreCase(existinglink.getRel(), "related")) {
					links.add(existinglink);

				}
			}

			existingEntry.setLink(links);
		}
		existingEntry.setTitle(entry.getTitle());

//		for (EscenicContent escenicContent : escenicContentList) {
//			if (escenicContent != null) {
//				existingLinks.addAll(escenicContent.getLinks());
//			}
//		}
		return existingEntry;

	}

	private List<Link> generateLinksForArticle(List<EscenicContent> escenicContentList) {
		List<Link> links = new ArrayList<>();

		boolean thumbnailExist = false;
		for (EscenicContent escenicContent : escenicContentList) {
			if (escenicContent != null) {

				if (escenicContent instanceof EscenicImage) {
					EscenicImage escenicImage = (EscenicImage)escenicContent;
					if (!thumbnailExist) {
						Link thumbnailLink = new Link();
						thumbnailLink.setHref(escenicImage.getThumbnailUrl());
						thumbnailLink.setRel("thumbnail");
						thumbnailLink.setType("image/png");
						links.add(thumbnailLink);
					}


					//TODO does this happen automatically? Do we define pictureRel along side the inline relations?
					Link pictureRelLink = new Link();
					Payload payload1 = new Payload();
					List<Field> fields1 = new ArrayList<>();
					fields1.add(createField("title", escenicImage.getTitle(), null,null));
					fields1.add(createField("caption", escenicImage.getTitle(), null, null));
					payload1.setField(fields1);
					payload1.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-summary/picture");
					pictureRelLink.setPayload(payload1);
					pictureRelLink.setGroup("pictureRel");
					pictureRelLink.setHref(escenicContent.getEscenicLocation());
					pictureRelLink.setThumbnail(escenicImage.getThumbnailUrl());
					pictureRelLink.setRel("related");
					pictureRelLink.setState("published");
					pictureRelLink.setType("application/atom+xml; type=entry");
					pictureRelLink.setIdentifier(escenicContent.getEscenicId());
					links.add(pictureRelLink);


					Link link = new Link();
					Payload payload = new Payload();
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", escenicImage.getTitle(), null, null));
					fields.add(createField("caption", escenicImage.getTitle(), null, null));
					link.setGroup("com.escenic.inlineRelations");
					link.setThumbnail(escenicImage.getThumbnailUrl());
					payload.setField(fields);
					payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-summary/picture");
					link.setHref(escenicContent.getEscenicLocation());
					link.setType("application/atom+xml; type=entry");
					link.setRel("related");
					link.setState("published");
					link.setPayload(payload);
					link.setIdentifier(escenicContent.getEscenicId());

					link.setTitle(escenicImage.getTitle());
					links.add(link);

				} else if (escenicContent instanceof EscenicGallery) {
					EscenicGallery escenicGallery = (EscenicGallery)escenicContent;
					Link thumbnailLink = new Link();
					thumbnailLink.setHref(escenicGallery.getThumbnailUrl());
					thumbnailLink.setRel("thumbnail");
					thumbnailLink.setType("image/png");
					links.add(thumbnailLink);

					Link link = new Link();
					Payload payload = new Payload();
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", "test", null, null));
					fields.add(createField("caption", "test", null, null));
					link.setGroup("com.escenic.inlineRelations");
					link.setThumbnail(escenicGallery.getThumbnailUrl());
					payload.setField(fields);
					payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-summary/gallery");
					link.setHref(escenicContent.getEscenicLocation());
					link.setType("application/atom+xml; type=entry");
					link.setRel("related");
					link.setState("published");
					link.setPayload(payload);
					link.setIdentifier(escenicContent.getEscenicId());

					link.setTitle(escenicGallery.getTitle());
					links.add(link);

				} else if (escenicContent instanceof EscenicEmbed) {
					EscenicEmbed escenicEmbed = (EscenicEmbed)escenicContent;
					Link link = new Link();
					Payload payload = new Payload();
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", "adm embed", null, null));
					link.setGroup("com.escenic.inlineRelations");
					payload.setField(fields);
					payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-summary/socialembed");
					link.setHref(escenicEmbed.getEscenicLocation());
					link.setType("application/atom+xml; type=entry");
					link.setRel("related");
					link.setState("published");
					link.setPayload(payload);
					link.setIdentifier(escenicEmbed.getEscenicId());

					link.setTitle("adm embed");
					links.add(link);
				}
			}
		}
			return links;
	}

	protected static List<Field> generateImageFields(OneImageBean oneImageBean, com.atex.onecms.content.Content source, String location) {
		List<Field> fields = new ArrayList<Field>();

		//todo probably would be easier to create a custom type? that hold some of the info in single place instead of checking mutliple places...
//		FilesAspectBeansource.getContent().getAspect(FilesAspectBean.ASPECT_NAME)


		fields.add(createField("name", oneImageBean.getName(), null, null)); //todo should this be our name?
		if (StringUtils.isEmpty(oneImageBean.getName())) {
			fields.add(createField("title", "No Name", null, null)); //todo should this be our name?
		} else {
			fields.add(createField("title", oneImageBean.getName(), null, null)); //todo should this be our name?
		}

		fields.add(createField("description", oneImageBean.getDescription(), null, null)); //todo should this be our name?
		fields.add(createField("caption", oneImageBean.getCaption(), null, null)); //todo should this be our name?
		fields.add(createField("photographer", oneImageBean.getAuthor(), null, null));
		fields.add(createField("alttext", null, null, null));
		fields.add(createField("copyright", oneImageBean.getCredit(), null, null));

		if (StringUtils.isNotBlank(location)) {
			/**
			 *
			 *
			 * TODO BELOW!!
			 */

			Link link = new Link();
			link.setHref(location);
			link.setRel("edit-media");

			Aspect filesAspect = source.getAspect(FilesAspectBean.ASPECT_NAME);
			FilesAspectBean fab = (FilesAspectBean) filesAspect.getData();
			String imgExt = "jpeg";
			for (ContentFileInfo f : fab.getFiles().values()) {
				System.out.println("setting ext : " + f.getFilePath().substring(f.getFilePath().lastIndexOf('.') + 1));
				imgExt = f.getFilePath().substring(f.getFilePath().lastIndexOf('.') + 1);
			}
			if (StringUtils.equalsIgnoreCase(imgExt, "jpg")) {
				imgExt = "jpeg";
			}
			link.setType("image/" + imgExt);

			/**
			 *
			 *
			 * TODO ABOVE!!
			 */

			link.setTitle(oneImageBean.getName());
			fields.add(createField("binary", link, null, null));
		}
		return fields;
	}

	protected static Value createValue(String fieldName, Object value) {
		if ( value != null) {
			if (value instanceof String) {
				if (!StringUtils.equalsIgnoreCase(fieldName, "body") && !StringUtils.equalsIgnoreCase(fieldName,"embedCode")) {
					value = escapeHtml(value.toString());
				}
			}
			return new Value(Arrays.asList(new Object[]{value}));
		} else {
			return null;
		}

	}

	protected static Field createField(String fieldName, Object value, List<Field> fields, com.atex.onecms.app.dam.integration.camel.component.escenic.model.List list) {
		return new Field(fieldName, createValue(fieldName,value), fields, list);
	}



	protected List<Field> generateArticleFields(OneArticleBean oneArticleBean, ContentResult source, List<EscenicContent> escenicContentList, Entry existingEntry) {

		List<Field> fields = new ArrayList<Field>();

		CustomArticleBean articleBean = (CustomArticleBean)oneArticleBean;
		fields.add(createField("title", (articleBean.getName()), null, null)); //todo should this be our name?
		fields.add(createField("headlinePrefix", articleBean.getHeadlinePrefix(), null, null));
		fields.add(createField("articleFlagLabel", "none", null, null));//TODO
		fields.add(createField("articleLayout", articleBean.getArticleType().toLowerCase(), null, null));
		fields.add(createField("headline", getStructuredText(articleBean.getSubHeadline()), null, null)); //TODO Check what needs to go in there..
		fields.add(createField("scoreReview","", null, null)); //TODO
		fields.add(createField("byline",articleBean.getByline(), null, null));
		fields.add(createField("originalSource",articleBean.getSource(), null, null));
		fields.add(createField("leadtext",getStructuredText(articleBean.getLead()), null, null));
		fields.add(createField("body", convertStructuredTextToEscenic(getStructuredText(articleBean.getBody()), escenicContentList), null, null));
		fields.add(createField("summaryIcon","automatic", null, null));
		fields.add(createField("appearInLatestNews","true", null, null));
		fields.add(createField("appearInNLAFeed","true", null, null));

//		fields.add(createField("displayDate",null)); //TODO needed?



		// TODO HOW DO WE KNOW??
		fields.add(createField("authorBioRel",null, null, null)); //TODO more complicated - list of relations? mediatype=com.escenic.relationType
		fields.add(createField("teaserRel",null, null, null)); //TODO more complicated - list of relations? mediatype=com.escenic.relationType
		fields.add(createField("pictureRel",null, null, null)); //TODO more complicated - list of relations? mediatype=com.escenic.relationType
		fields.add(createField("sidebarRel",null, null, null)); //TODO more complicated - list of relations? mediatype=com.escenic.relationType
		fields.add(createField("binary",null, null, null)); //TODO more complicated - list of relations? mediatype=com.escenic.relationType
		/**
		 * position1Rel - position20Rel
		 * same definition
		 * list of relations? mediatype=com.escenic.relationType
		 */
//	    fields.add(createField("columnistRel",null)); //TODO more complicated - list of relations? mediatype=com.escenic.relationType
//		fields.add(createField("columnistMainRel",null)); //TODO more complicated - list of relations? mediatype=com.escenic.relationType
//		fields.add(createField("sponsorLogoRel",null)); //TODO more complicated - list of relations? mediatype=com.escenic.relationType
//		fields.add(createField("geolocation",null)); //TODO are we using gmaps at all? just ignore this field for now
//		fields.add(createField("historicalAmpUrls",null)); //TODO no field for that in desk.
//		fields.add(createField("appearInLatestNews",null));  //TODO no field for that in desk. - boolean true/false
//		fields.add(createField("breakingnews",null)); //TODO no field for that in desk. - boolean true/false
//		fields.add(createField("premium",null)); //TODO no field for that in desk. - boolean true/false
//		fields.add(createField("datawallProtected",null)); //TODO no field for that in desk. - string but default exists - not needed?
//		fields.add(createField("subscriptionProtected",null));//TODO no field for that in desk. - string but default exists - not needed?
//		fields.add(createField("sponsored",null)); //TODO no field for that in desk. - boolean true/false
//		fields.add(createField("sponsoredByText",null)); //TODO no field for that in desk. - string but default exists - not needed?
//		fields.add(createField("targetWebsite",null)); //TODO no field for that in desk. - boolean true/false
//		fields.add(createField("targetAdaptive",null)); //TODO no field for that in desk. - boolean true/false
//		fields.add(createField("targetMobile",null)); //TODO no field for that in desk. - boolean true/false
//		fields.add(createField("ageGate",null)); //TODO no field for that in desk. - boolean true/false
//		fields.add(createField("disableAdBlockerPopup",null)); //TODO no field for that in desk. - boolean true/false
		return fields;
	}

	//todo review
	protected static String convertStructuredTextToEscenic(String structuredText, List<EscenicContent> escenicContentList) {
		System.out.println("Before replacing embeds:\n" + structuredText);
		structuredText = StringEscapeUtils.unescapeHtml(structuredText);
		structuredText = structuredText.replaceAll("\u00a0","");
		structuredText = "<div xmlns=\"http://www.w3.org/1999/xhtml\">" + structuredText + "</div>";
		String test = replaceEmbeds(structuredText, escenicContentList);
//		test = "<![CDATA[" + test + "]]>";
		System.out.println("After replacing embeds:\n" + test);
		return test;
	}


	//todo instead of list of image list of generic content type and check for specific instance video, soundcloud, image, embed?
	protected static String replaceEmbeds(String structuredText, List<EscenicContent> escenicContentList) {
		final String body = customEmbedParser.processSmartEmbedToHtml(structuredText, (e) -> {
			final CustomEmbedParser.SmartEmbed embed = customEmbedParser.createSmartEmbedFromElement(e);
			if (embed != null) {

				for(EscenicContent escenicContent : escenicContentList)  {
					switch (embed.getObjType()) {

						case IMAGE_TYPE :
							//todo here check the embed type and generate different html tags based on that.?
							if (escenicContent != null && escenicContent instanceof EscenicImage) {
								final ContentId id = embed.getContentId();
								if (id != null && escenicContent.getOnecmsContentId() != null) {
									if (id.equals(escenicContent.getOnecmsContentId())) {

										try{
											EscenicImage escenicImage = (EscenicImage) escenicContent;
											String newHtml;
											if (StringUtils.isNotEmpty(escenicImage.getId())) {
												 newHtml = "<p><img src=\"" + escenicImage.getThumbnailUrl() + "\" alt=\"undefined\" id=\"" + escenicImage.getId() + "\"></img></p>";
											} else {
												newHtml = "<p><img src=\"" + escenicImage.getThumbnailUrl() + "\" alt=\"undefined\"></img></p>";
											}


											final Element ne = Jsoup.parseBodyFragment(newHtml).body().child(0);
											e.replaceWith(ne);
										} catch (ClassCastException classCastException) {
											throw new RuntimeException("Error occurred while attempting to cast EscenicContent to EscenicImage: " + classCastException.getMessage());
										}
									}
								}
							}

							break;
						case GALLERY_TYPE :

							if (escenicContent != null && escenicContent instanceof EscenicGallery) {
								final ContentId id = embed.getContentId();
								if (id != null && escenicContent.getOnecmsContentId() != null) {
									if (id.equals(escenicContent.getOnecmsContentId())) {

										try{
											EscenicGallery escenicGallery = (EscenicGallery) escenicContent;
											String newHtml;
											if (StringUtils.isNotEmpty(escenicGallery.getId())) {
												newHtml = "<p><a href=\"" + escenicGallery.getEscenicLocation() + "\" id=\"" + escenicGallery.getId() + "\" alt=\"undefined\"></a></p>";
											} else {
												newHtml = "<p><a href=\"" + escenicGallery.getEscenicLocation() + "\" alt=\"undefined\"></a></p>";
											}

//											final String newHtml = "<p><a href=\"" + escenicGallery.getEscenicLocation() + "\" id=\"" + escenicGallery.getId() + "\" alt=\"undefined\"></a></p>";
											final Element ne = Jsoup.parseBodyFragment(newHtml).body().child(0);
											e.replaceWith(ne);
										} catch (ClassCastException classCastException) {
											throw new RuntimeException("Error occurred while attempting to cast EscenicContent to EscenicImage: " + classCastException.getMessage());
										}
									}
								}
							}

							break;

						case "video" :

//							if (escenicContent != null && escenicContent instanceof ) {
//								final ContentId id = embed.getContentId();
//								if (id != null && escenicContent.getOnecmsContentId() != null) {
//									if (id.equals(escenicContent.getOnecmsContentId())) {
//
//										try{
//											EscenicGallery escenicGallery = (EscenicGallery) escenicContent;
//											final String newHtml = "<p><a href=\"" + escenicGallery.getEscenicLocation() + "\" alt=\"undefined\"></a></p>";
//											final Element ne = Jsoup.parseBodyFragment(newHtml).body().child(0);
//											e.replaceWith(ne);
//										} catch (ClassCastException classCastException) {
//											throw new RuntimeException("Error occurred while attempting to cast EscenicContent to EscenicImage: " + classCastException.getMessage());
//										}
//									}
//								}
//							}
//
//							break;

						case SOCIAL_EMBED_TYPE:
							if (escenicContent != null && escenicContent instanceof EscenicEmbed) {
								EscenicEmbed escenicEmbed = (EscenicEmbed)escenicContent;
								if (StringUtils.isNotEmpty(embed.getEmbedUrl()) && StringUtils.equalsIgnoreCase(escenicEmbed.getEmbedUrl(), embed.getEmbedUrl())) {
									//todo here check the embed type and generate different html tags based on that.?
//												final String newHtml = "<p><a href=\"" + escenicEmbed.getEscenicLocation() + "\" alt=\"undefined\">adm embed</a></p>";

									String newHtml;
									if (StringUtils.isNotEmpty(escenicEmbed.getId())) {
										newHtml = "<p><a href=\"" + escenicEmbed.getEscenicLocation() + "\" id=\"" + escenicEmbed.getId() + "\">adm embed</a></p>";
									} else {
										newHtml = "<p><a href=\"" + escenicEmbed.getEscenicLocation() + "\">adm embed</a></p>";
									}

//									/**/final String newHtml = "<p><a href=\"" + escenicEmbed.getEscenicLocation() + "\" id=\"" + escenicEmbed.getId() + "\">adm embed</a></p>";
//												final String newHtml = escenicEmbed.getEmbedCode();
									final Element ne = Jsoup.parseBodyFragment(newHtml).body().child(0);
									e.replaceWith(ne);
								}
							break;

					}

				}
				}
			}
		});
		return body;
	}

	protected static String addOnecmsIdToSocialEmbeds(String structuredText, List<EscenicContent> escenicContentList) {
		final String body = customEmbedParser.processSmartEmbedToHtml(structuredText, (e) -> {
			final CustomEmbedParser.SmartEmbed embed = customEmbedParser.createSmartEmbedFromElement(e);
			if (embed != null) {
				for(EscenicContent escenicContent : escenicContentList) {
					if (escenicContent.isInlineElement()) {
						if (escenicContent != null && (escenicContent instanceof EscenicImage || escenicContent instanceof EscenicGallery)) {
//						||	escenicContent instanceof EscenicVideo)

							if (escenicContent.getOnecmsContentId() != null) {
								if (StringUtils.isNotEmpty(escenicContent.getEscenicId()) && embed.getContentId() != null &&
									StringUtils.equalsIgnoreCase(IdUtil.toIdString(escenicContent.getOnecmsContentId()), IdUtil.toIdString(embed.getContentId()))) {
									e.attr("escenic-id", escenicContent.getEscenicId());
								}
							} else {
								throw new RuntimeException("EscenicContent onemcsid was null");
							}
						}
						//do we need to set it on the escenicContent as well?
						if (escenicContent != null && escenicContent instanceof EscenicEmbed) {
							EscenicEmbed escenicEmbed = (EscenicEmbed) escenicContent;
//								System.err.println("ITWAS ")
							if (StringUtils.isNotEmpty(embed.getEmbedUrl()) && StringUtils.isNotEmpty(escenicEmbed.getEmbedUrl()) && StringUtils.equalsIgnoreCase(escenicEmbed.getEmbedUrl(), embed.getEmbedUrl())) {

								if (escenicEmbed.getOnecmsContentId() != null) {
									e.attr("data-onecms-id", IdUtil.toIdString(escenicEmbed.getOnecmsContentId()));

								}

								if (StringUtils.isNotEmpty(escenicContent.getEscenicId())) {
									e.attr("escenic-id", escenicContent.getEscenicId());
								}
							}
						}
					}

				}
			}
		});
		return body;
	}


	protected String getStructuredText(StructuredText str) {
		if (str != null)
			return str.getText();
		else
			return "";
	}

	protected static StringEntity generateAtomEntity(String xmlContent) {
		StringEntity entity = new StringEntity(xmlContent, StandardCharsets.UTF_8);
		entity.setContentType("application/atom+xml");
		return entity;
	}

	private static ByteArrayEntity generateImageEntity(ByteArrayOutputStream baos, String imgExt) {
		ByteArrayEntity entity = new ByteArrayEntity(baos.toByteArray());
		entity.setContentType("image/" + imgExt);
		return entity;
	}

	protected static Header generateContentTypeHeader(String contentType ) {
		return new BasicHeader(HttpHeaders.CONTENT_TYPE, contentType);
	}

	protected static Header generateAuthenticationHeader(EscenicConfig escenicConfig) {
		String username = escenicConfig.getUsername();
		String password = escenicConfig.getPassword();

		if (StringUtils.isEmpty(username) && StringUtils.isEmpty(password)) {
			throw new RuntimeException("Unable to access username & password for escenic");
		}

		String encoding = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

		return new BasicHeader(HttpHeaders.AUTHORIZATION, AUTH_PREFIX + encoding);
	}

	protected static CloseableHttpResponse sendNewContentToEscenic(String xmlContent, EscenicConfig escenicConfig) throws URISyntaxException, IOException {

		//todo read the value  + modify it?
		if (StringUtils.isEmpty(escenicConfig.getApiUrl())) {
			throw new RuntimeException("Blank or missing escenic ApiUrl");
		}

		String url = escenicConfig.getApiUrl();
		HttpPost request = new HttpPost(url);
		CloseableHttpClient httpClient = HttpClients.createDefault();
		request.setEntity(generateAtomEntity(xmlContent));
		request.expectContinue();
		request.setHeader(generateAuthenticationHeader(escenicConfig));
		request.setHeader(generateContentTypeHeader("application/atom+xml"));

		System.out.println("Sending the following xml to escenic:" + xmlContent);

		try {
			CloseableHttpResponse result = httpClient.execute(request);
			System.out.println(result.getStatusLine());
			System.out.println(EntityUtils.toString(result.getEntity()));
			//todo probably check the status code?
			return result;
		} catch(Exception e) {
			System.out.println("Error :" + e.getMessage());
			System.out.println("Error :" + e.getCause());
		} finally {
			request.releaseConnection();
		}
		return null;
	}

	protected static CloseableHttpResponse sendUpdatedContentToEscenic(String url, String xmlContent, EscenicConfig escenicConfig) {
		HttpPut request = new HttpPut(url);

		CloseableHttpClient httpClient = HttpClients.createDefault();

		request.setEntity(generateAtomEntity(xmlContent));
		request.expectContinue();
		request.setHeader(generateAuthenticationHeader(escenicConfig));
		request.setHeader(generateContentTypeHeader("application/atom+xml"));
		//TODO we might need to do additonal querying here...
		request.setHeader(HttpHeaders.IF_MATCH, "*");
		System.out.println("Sending the following UPDATE xml to escenic:" + xmlContent);

		try {
			CloseableHttpResponse result = httpClient.execute(request);
//			System.out.println("result: " + EntityUtils.toString(result.getEntity()));
			System.out.println(result.getStatusLine());
			//todo probably check the status code?
			return result;
		} catch(Exception e) {
			System.out.println("Error - :" + e.getMessage());
			System.out.println("Error - :" + e.getCause());
		} finally {
			request.releaseConnection();
		}
		return null;

	}

	protected static Entry deserializeXml(String xml) throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(Entry.class);
		System.out.println("jaxbContext is=" +jaxbContext.toString());
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		Entry entry = (Entry) unmarshaller.unmarshal( new StringReader(xml));
//		entry.getLink().forEach(x -> System.out.println("href: " + x.getHref() + "\ntype: " + x.getType() + "\ntitle: " + x.getTitle() + "\nrel: " + x.getRel() + "\n\n"));
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
//			marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new DefaultNamespacePrefixMapper());
			marshaller.setProperty(CharacterEscapeHandler.class.getName(), new CustomCharacterEscapeHandler());
			//marshaller.marshal(object, new OutputStreamWriter(System.out));
//			marshaller.setProperty(MarshallerProperties.CHARACTER_ESCAPE_HANDLER,
//				new org.eclipse.persistence.oxm.CharacterEscapeHandler() {
//					@Override
//					public void escape(char[] ac, int i, int j, boolean flag,
//									   Writer writer) throws IOException {
//						writer.write(ac, i, j);
//					}
//				});
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

	protected CloseableHttpResponse processImage(ContentResult imgCr, Entry existingImgEntry, String existingEscenicLocation, PolicyCMServer cmServer, EscenicConfig escenicConfig, EscenicImage escenicImage) throws IOException, CMException, URISyntaxException {
//		List<CloseableHttpResponse> responses = new ArrayList<>();
		CloseableHttpResponse response = null;
		String binaryUrl = escenicConfig.getBinaryUrl();
		if (StringUtils.isEmpty(binaryUrl)) {
			throw new RuntimeException("Unable to send image to Escenic as binaryUrl is blank");
		}

		if (imgCr != null) {
			Object contentBean = extractContentBean(imgCr);
			OneImageBean oneImageBean = null;
			if (contentBean != null && contentBean instanceof OneImageBean) {
				oneImageBean = (OneImageBean) contentBean;
			}

			if (oneImageBean != null) {
				//TODO crops;
				extractCrops(imgCr);

				if (StringUtils.isNotEmpty(oneImageBean.getTitle())) {
					escenicImage.setTitle(oneImageBean.getName());
				} else {
					escenicImage.setTitle("No title");
				}


				String binaryLocation = null;
				if (existingImgEntry == null) {
					//i.e it's a brand new content that doesn't exist in escenic...
					//todo this needs to be reworked.
					binaryLocation = sendBinary(imgCr.getContent(), oneImageBean, cmServer, binaryUrl, escenicConfig);
				}

				Entry atomEntry = constructAtomEntryForBinaryImage(oneImageBean, existingImgEntry, imgCr.getContent(), binaryLocation);
				if (existingImgEntry != null) {
					atomEntry = processExistingImage(existingImgEntry, atomEntry);
				}
				String xml = serializeXml(atomEntry);
				if (StringUtils.isNotEmpty(xml) && existingImgEntry != null) {
					response = sendUpdatedContentToEscenic(existingEscenicLocation, xml, escenicConfig);
				} else {
					response = sendNewContentToEscenic(xml, escenicConfig);
				}

			} else {
				log.error("Image Bean was null");

			}
		} else {
			log.error("Image cr was null");

		}
		return response;
	}

	private Entry processExistingImage(Entry existingEntry, Entry entry) {
		if (existingEntry != null && entry != null) {
			List<Field> existingFields = existingEntry.getContent().getPayload().getField();
			List<Field> newFields = entry.getContent().getPayload().getField();
			for (Field field : existingFields) {
				for (Field newField : newFields) {
					//modify all fields apart binary location.
					if (StringUtils.isNotBlank(field.getName()) && !StringUtils.equalsIgnoreCase(field.getName(), "binary")) {
						if (StringUtils.equalsIgnoreCase(field.getName(), newField.getName())) {
							field.setValue(newField.getValue());
						}
					}
				}
			}

			existingEntry.setControl(entry.getControl());
			existingEntry.setTitle(entry.getTitle());
//			existingEntry.setLink(entry.getLink());
			List<Link> existingLinks = existingEntry.getLink();
			List<Link> links = entry.getLink();

			if (existingLinks != null && links != null) {
				for (Link existinglink : existingLinks) {

					boolean found = false;

					for (Link link : links) {

						if (StringUtils.equalsIgnoreCase(existinglink.getHref(), link.getHref()) &&
							StringUtils.equalsIgnoreCase(existinglink.getIdentifier(), link.getIdentifier()) &&
							StringUtils.equalsIgnoreCase(existinglink.getType(), link.getType())) {

							//it's the same link...
							found = true;
						}
					}

					if (!found && !StringUtils.equalsIgnoreCase(existinglink.getRel(), "related")) {
						links.add(existinglink);

					}
				}
				existingEntry.setLink(links);
			}


			return existingEntry;
		}
			return entry;
	}

	private void extractCrops(ContentResult<OneImageBean> imgCr) {

//		"aspects": {
//			"atex.ImageEditInfo": {
//				"data": {
//					"_type": "atex.ImageEditInfo",
//						"crops": {
//						"default_type": "com.atex.onecms.image.CropInfo",
//							"default": {
//							"_type": "com.atex.onecms.image.CropInfo",
//								"cropRectangle": {
//								"height": 1146,
//									"x": 628,
//									"y": 84,
//									"width": 2036
//							},
//							"imageFormat": {
//								"name": "default",
//									"description": "Crop has freeform aspect ratio",
//									"label": "Default"
//							}
//						},
//						"1x1_type": "com.atex.onecms.image.CropInfo",
//							"1x1": {
//							"_type": "com.atex.onecms.image.CropInfo",
//								"cropRectangle": {
//								"height": 1962,
//									"x": 597,
//									"y": 393,
//									"width": 1963
//							},
//							"imageFormat": {
//								"name": "1x1",
//									"aspectRatio": {
//									"height": 1,
//										"width": 1
//								},
//								"description": "Crop has a 1x1 aspect ratio"
//							}
//						},
//						"2x1_type": "com.atex.onecms.image.CropInfo",
//							"2x1": {
//							"_type": "com.atex.onecms.image.CropInfo",
//								"cropRectangle": {
//								"height": 1188,
//									"x": 324,
//									"y": 712,
//									"width": 2381
//							},
//							"imageFormat": {
//								"name": "2x1",
//									"aspectRatio": {
//									"height": 1,
//										"width": 2
//								},
//								"description": "Crop has a 2x1 aspect ratio"
//							}
//						},
//						"3x2_type": "com.atex.onecms.image.CropInfo",
//							"3x2": {
//							"_type": "com.atex.onecms.image.CropInfo",
//								"cropRectangle": {
//								"height": 1612,
//									"x": 31,
//									"y": 52,
//									"width": 2418
//							},
//							"imageFormat": {
//								"name": "3x2",
//									"aspectRatio": {
//									"height": 2,
//										"width": 3
//								},
//								"description": "Crop has a 3x2 aspect ratio"
//							}
//						}
//					},


	}

	private String sendBinary(com.atex.onecms.content.Content cresultContent, OneImageBean oneImageBean, PolicyCMServer cmServer, String binaryUrl, EscenicConfig escenicConfig) throws IOException, CMException {
//		"aspects": {
//			"atex.Files": {
//				"data": {
//					"_type": "atex.Files",
//						"files": {
//						"5test-test_Filedate_newer_IPTCDATE-200505050505_948422600.jpg_type": "com.atex.onecms.content.ContentFileInfo",
//							"5test-test_Filedate_newer_IPTCDATE-200505050505_948422600.jpg": {
//							"_type": "com.atex.onecms.content.ContentFileInfo",
//								"fileUri": "content://000000000006ba70/5test-test-Filedate-newer-IPTCDATE-200505050505-948422600-jpg",
//								"filePath": "5test-test_Filedate_newer_IPTCDATE-200505050505_948422600.jpg"
//						}
//					}
//				},
//				"version": "onecms:8835f4f1-9258-4d5b-93e2-1bb8d455ae50:55a48dba-c018-45b0-8bf0-3e5b0502da76"
//			}
		List<FileType> file;
		Aspect filesAspect = cresultContent.getAspect("atex.Files");
		FilesAspectBean fab = (FilesAspectBean) filesAspect.getData();

		file = new ArrayList<>();
		List<String> binaryLocations = new ArrayList<>();
		for (ContentFileInfo f : fab.getFiles().values()) {

			FileType image = new FileType();
			image.setEncoding("base64");
			image.setName("image/" + f.getFilePath().toLowerCase());

			InputStream is = getImageInputStream(cmServer, cresultContent, cresultContent);

			String imgExt = f.getFilePath().substring(f.getFilePath().lastIndexOf('.') + 1);

			System.out.println("image extention = " + imgExt);

			if (image.getName().endsWith(".jpg") || image.getName().endsWith(".jpeg")) {
				try {
					JPEGWriter jpegWriter = new JPEGWriter();

					jpegWriter.load(is);

					IPTCMetadata iptc = jpegWriter.getIPTCMetadata();

					iptc.setDescription(clean(oneImageBean.getCaption()));
					iptc.setTitle(clean(oneImageBean.getDescription()));
					iptc.setCopyright(clean(oneImageBean.getReporter()));
					iptc.setSource(clean(oneImageBean.getCredit()));

					jpegWriter.replaceIPTCMetadata(iptc.getPhotoshopApp13Data());

					ByteArrayOutputStream baos = new ByteArrayOutputStream();

					jpegWriter.write(baos);

					is = new ByteArrayInputStream(baos.toByteArray());


					String location = sendImage(baos, imgExt, binaryUrl, escenicConfig);
					binaryLocations.add(location);
//					String result = constructAtomEntryForBinary(oneImageBean, cresultContent, location);
//					CloseableHttpResponse response = sendNewArticleToEscenic("http://inm-test-editorial.inm.lan:8081/webservice/escenic/section/1/content-items",result);
//					if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED || response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
//						Header locationHeader = response.getLastHeader("Location");
//						if (locationHeader != null && StringUtils.isNotEmpty(locationHeader.getValue())) {
//							binaryLocations.add(locationHeader.getValue());
//
//						}
//					}


				} catch (Exception e) {
					e.printStackTrace();
				}
			}


//			Base64InputStream b64is = new Base64InputStream(is, true);
//			image.setEncodedFile(StreamUtil.readFullyToString(b64is, "ASCII"));
//			file.add(image);

		}
		if (binaryLocations.size() > 0) {
			return binaryLocations.get(0);
		} else {
			return null;
		}
	}

	private String sendImage(ByteArrayOutputStream baos, String imgExt, String binaryUrl, EscenicConfig escenicConfig){
//			HttpPost request = new HttpPost("http://inm-test-editorial.inm.lan:8081/webservice/escenic/binary");

		HttpPost request = new HttpPost(binaryUrl);
		CloseableHttpClient httpClient = HttpClients.createDefault();
		ByteArrayEntity entity  = generateImageEntity(baos, imgExt);
		request.setEntity(entity);
		request.expectContinue();
		request.setHeader(generateAuthenticationHeader(escenicConfig));
		request.setHeader(generateContentTypeHeader("image/" + imgExt));
		System.out.println("Sending the following xml to escenic:" + baos.toByteArray());

		try {
			CloseableHttpResponse result = httpClient.execute(request);



			//TODO HERE evaluate the response properly... then extract the header
			System.out.println(result.getStatusLine());
			//todo probably check the status code?
			for (Header h : result.getAllHeaders()){
				System.out.println(h.getName() + " : " + h.getValue());
				if (StringUtils.equalsIgnoreCase(h.getName(), "Location")) {
					return h.getValue();
//						constructAtomEntryForBinary()
				}
			}
//				return result;
		} catch(Exception e) {
			System.out.println("Error :" + e.getMessage());
			System.out.println("Error :" + e.getCause());
		} finally {
			request.releaseConnection();
		}
		return null;
	}

	private static Entry constructAtomEntryForBinaryImage(OneImageBean oneImageBean, Entry existingImgEntry, com.atex.onecms.content.Content cresultContent, String binaryLocation) {
		if (oneImageBean != null) {
			Entry entry = new Entry();
			Title title = new Title();
			title.setType("text");
			if (StringUtils.isEmpty(oneImageBean.getName())) {
				title.setTitle("No Name");
			} else {
				title.setTitle(oneImageBean.getName());
			}

			Payload payload = new Payload();
			Content content = new Content();

			Control control = new Control();
			State state = new State();

			state.setName("published");
			//TODO is it needed? try without it..
//			state.setHref("http://inm-test-editorial.inm.lan:8081/webservice/escenic/content/state/published/editor");
			state.setState("published");
//			List<State> states = new ArrayList<>();
//			states.add(state);
			control.setState(Arrays.asList(state));
			control.setDraft("no");

			List<Field> fields = generateImageFields(oneImageBean, cresultContent, binaryLocation);
			payload.setField(fields);

			//TODO this has to be determined by the type.. if we are going to use the same method for different binary files
			payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-type/picture");

			content.setPayload(payload);
			content.setType("application/vnd.vizrt.payload+xml");

			entry.setContent(content);
			entry.setControl(control);

			return entry;
		} else {
			//todo.
			throw new RuntimeException("Could not read properties of an image " + cresultContent);
		}
	}

	protected static InputStream getImageInputStream(PolicyCMServer cmServer,
												  com.atex.onecms.content.Content<?> cresultContent,
												  com.atex.onecms.content.Content<ImageContentDataBean> content) throws CMException, IOException {
		String secret = (new ImageServiceConfigurationProvider(cmServer)).getImageServiceConfiguration().getSecret();

		ImageServiceUrlBuilder urlBuilder = new ImageServiceUrlBuilder(content, secret).aspectRatio(4,3);
		Aspect imageEditAspect = cresultContent.getAspect("atex.ImageEditInfo");

		ImageEditInfoAspectBean imageEditInfo = null;
		if (imageEditAspect != null) {
			imageEditInfo = (ImageEditInfoAspectBean) imageEditAspect.getData();
		}

		ImageInfoAspectBean ii = (ImageInfoAspectBean) cresultContent.getAspect(ImageInfoAspectBean.ASPECT_NAME).getData();

		if (imageEditInfo != null) {
			CropInfo crop = imageEditInfo.getCrop("3x2");

			if (crop != null) {
				Rectangle rect = crop.getCropRectangle();

				if (rect.getWidth() + rect.getX() > ii.getWidth()) {
					rect.setWidth(ii.getWidth() - rect.getX());
				}
				if (rect.getHeight() + rect.getY() > ii.getHeight()) {
					rect.setHeight(ii.getHeight() - rect.getY());
				}
				urlBuilder.crop(rect);
			}
		}

		float pixels = ii.getHeight() * ii.getWidth();

		if (pixels > MAX_IMAGE_SIZE) {

			double scalingRatio = Math.sqrt(MAX_IMAGE_SIZE / pixels);

			int newHeight = (int) Math.min (Math.floor(ii.getHeight() * scalingRatio),ii.getHeight());
			int newWidth = (int) Math.min (Math.floor(ii.getWidth() * scalingRatio), ii.getWidth());

			java.util.logging.Logger.getGlobal().warning("Image is too big - scaling down to w" + newWidth + " x h" + newHeight);
			urlBuilder.width(newWidth).height(newHeight);
		}


		java.net.URL url = new URL ("http://localhost:8080" + urlBuilder.buildUrl());


		return url.openStream();

	}

	private String clean(String component) {
		if (component == null) return null;

		return CharMatcher.ASCII.retainFrom(component);

	}



	/**
	 * Constructing an atom entry for binary file:
	 * curl --include -u jwojcik@atex.com:4l+rUbruQE -X POST -H "Content-Type: application/atom+xml" http://inm-test-editorial.inm.lan:8081/webservice/escenic/section/1/content-items --upload-file imageatom.xml
	 *
//	 * @param binaryLocation
	 * @return
	 */
	private static String constructAtomEntryForSocialEmbed(EscenicEmbed escenicEmbed, CustomEmbedParser.SmartEmbed socialEmbed) {
		Entry entry = new Entry();
		Title title = new Title();
		title.setType("text");
//		if (StringUtils.isEmpty(socialEmbed.g)) {
//			title.setTitle("No Name");
//		} else {
			title.setTitle("embed test");
//		}
		entry.setTitle(title);
		Payload payload = new Payload();
		Content content = new Content();

		Control control = new Control();
		State state = new State();
		state.setName("published");
//		state.setHref("http://inm-test-editorial.inm.lan:8081/webservice/escenic/content/state/published/editor");
		state.setState("published");
//		List<State> states = new ArrayList<>();
//		states.add(state);
//		control.setState(states);
		control.setState(Arrays.asList(state));
		control.setDraft("no");

		List<Field> fields = generateSocialEmbedFields(socialEmbed);
		payload.setField(fields);

		//TODO this has to be determined by the type.. if we are going to use the same method for different binary files
		payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-type/socialembed");

		content.setPayload(payload);
		content.setType("application/vnd.vizrt.payload+xml");

		entry.setContent(content);
		entry.setControl(control);

		return serializeXml(entry);
	}

	protected static List<Field> generateSocialEmbedFields(CustomEmbedParser.SmartEmbed socialEmbed) {
		List<Field> fields = new ArrayList<Field>();

		//todo probably would be easier to create a custom type? that hold some of the info in single place instead of checking mutliple places...
//		FilesAspectBeansource.getContent().getAspect(FilesAspectBean.ASPECT_NAME)

//		fields.add(createField("embedCode", socialEmbed.getEmbedCode(), null, null));

		Payload p = new Payload();


		List<Field> embedFields = new ArrayList<Field>();

		List<Field> embedDetailsFields = new ArrayList<Field>();
		embedDetailsFields.add(createField("network", socialEmbed.getSocialNetwork(), null, null));
		embedDetailsFields.add(createField("url", socialEmbed.getEmbedUrl(), null, null));

//		embedDetailsFields.add(createField("embedCode", socialEmbed.getEmbedCode(), null, null));
		embedDetailsFields.add(createField("embedCode", "<![CDATA[" +socialEmbed.getEmbedCode() + "]]>", null, null));


		embedFields.add(createField("socialEmbeds", null, embedDetailsFields, null));
//		embedFields.add(createField("title", socialEmbed.getSocialNetwork(), null, null));

		p.setField(embedFields);

		com.atex.onecms.app.dam.integration.camel.component.escenic.model.List list = new com.atex.onecms.app.dam.integration.camel.component.escenic.model.List();
		list.setPayload(p);

		fields.add(createField("socialEmbeds", null, null , list));
		fields.add(createField("title", socialEmbed.getSocialNetwork(), null, null));

//		fields.add(createField("title", "No Name", null, null));
//		fields.add(createField("socialEmbeds", null, null, list));
//		fields.add(createField("socialEmbeds", ));



//		if (StringUtils.isEmpty(oneImageBean.getName())) {
//		fields.add(createField("title", "No Name"));
//		} else {
//			fields.add(createField("title", oneImageBean.getName())); //todo should this be our name?
//		}



//		Link link = new Link();
////		link.setHref(location);
//		link.setRel("edit-media");


//		link.setTitle(oneImageBean.getName()); //TODO this need to be based on the image
//		fields.add(createField("binary", link, null, null));
		return fields;
	}


	public List<CloseableHttpResponse> processEmbed(CustomEmbedParser.SmartEmbed embed, PolicyCMServer cmServer, EscenicConfig escenicConfig, EscenicEmbed escenicEmbed) throws IOException, URISyntaxException {
		List<CloseableHttpResponse> responses = new ArrayList<>();
		String xml = constructAtomEntryForSocialEmbed(escenicEmbed,embed);

		if (StringUtils.isNotEmpty(xml)) {
			responses.add(sendNewContentToEscenic(xml, escenicConfig));
		}
		return responses;


	}

	public List<CloseableHttpResponse> processGallery(DamCollectionAspectBean collectionAspectBean, Entry existingGalleryEntry, EscenicGallery escenicGallery, EscenicConfig escenicConfig) {

		List<CloseableHttpResponse> responses = new ArrayList<>();
		if (escenicGallery != null) {
//			String binaryUrl = escenicConfig.getApiUrl();


				String atomEntry = constructAtomEntryForGallery(collectionAspectBean, escenicGallery);
				if (StringUtils.isNotEmpty(atomEntry)) {
					try {
						responses.add(sendNewContentToEscenic(atomEntry, escenicConfig));
					} catch (URISyntaxException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		return responses;
	}





	/**
	 * Constructing an atom entry for binary file:
	 * curl --include -u jwojcik@atex.com:4l+rUbruQE -X POST -H "Content-Type: application/atom+xml" http://inm-test-editorial.inm.lan:8081/webservice/escenic/section/1/content-items --upload-file imageatom.xml
	 *
	 * 3
	 * @return
	 */
	private static String constructAtomEntryForGallery(DamCollectionAspectBean collectionAspectBean, EscenicGallery escenicGallery) {
		if (collectionAspectBean != null && escenicGallery != null) {
			Entry entry = new Entry();
			Title title = new Title();
			title.setType("text");
			if (StringUtils.isEmpty(collectionAspectBean.getName())) {
				title.setTitle("No Name");
			} else {
				title.setTitle(collectionAspectBean.getName());
			}

			Payload payload = new Payload();
			Content content = new Content();

			Control control = new Control();
			State state = new State();
			state.setName("published");
//			state.setHref("http://inm-test-editorial.inm.lan:8081/webservice/escenic/content/state/published/editor");
			state.setState("published");
//			List<State> states = new ArrayList<>();
//			states.add(state);
			control.setState(Arrays.asList(state));
			control.setDraft("no");

			List<Field> fields = generateGalleryFields(collectionAspectBean, escenicGallery);
			payload.setField(fields);

			List<Link> links = generateLinksForGallery(escenicGallery);

			//TODO how do we handle updates? we need to somehow merge existing links with the links we generate here...
			//todo we currently reuse whatever is in escenic - thing to consider is what if we want to remove a relationship? or replace it?
			entry.setLink(links);

			//TODO this has to be determined by the type.. if we are going to use the same method for different binary files
			payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-type/gallery");

			content.setPayload(payload);
			content.setType("application/vnd.vizrt.payload+xml");

			entry.setContent(content);
			entry.setControl(control);

			return serializeXml(entry);
		} else {
			//todo.
			throw new RuntimeException("Could not read properties of an image ");
		}
	}

	protected static List<Field> generateGalleryFields(DamCollectionAspectBean collectionAspectBean, EscenicGallery escenicGallery) {
		List<Field> fields = new ArrayList<>();

		fields.add(createField("name", collectionAspectBean.getName(), null, null));
		if (StringUtils.isEmpty(collectionAspectBean.getName())) {
			fields.add(createField("title", "No Name", null, null));
		} else {
			fields.add(createField("title", collectionAspectBean.getName(), null, null));
		}

		fields.add(createField("leadtext", collectionAspectBean.getHeadline(), null, null));

		return fields;
	}

	private static List<Link> generateLinksForGallery(EscenicGallery escenicGallery) {
		List<Link> links = new ArrayList<>();
		if (escenicGallery != null && !escenicGallery.getContentList().isEmpty()) {
			boolean thumbnailExist = false;
			for (EscenicContent escenicContent : escenicGallery.getContentList()) {
				if (escenicContent != null) {
					if (escenicContent instanceof EscenicImage) {
						EscenicImage escenicImage = (EscenicImage) escenicContent;
						if (!thumbnailExist) {
							if (StringUtils.isNotEmpty(escenicImage.getThumbnailUrl())) {
								thumbnailExist = true;
								Link thumbnailLink = new Link();
								thumbnailLink.setHref(escenicImage.getThumbnailUrl());
								thumbnailLink.setRel("thumbnail");
								thumbnailLink.setType("image/png");
								links.add(thumbnailLink);
							}
						}


						Link pictureRelLink = new Link();
						Payload payload1 = new Payload();
						List<Field> fields1 = new ArrayList<>();
						fields1.add(createField("title", escenicImage.getTitle(), null, null));
						fields1.add(createField("caption", escenicImage.getTitle(), null, null));
						payload1.setField(fields1);
						payload1.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-summary/picture");
						pictureRelLink.setPayload(payload1);
						pictureRelLink.setGroup("pictureRel");
						pictureRelLink.setHref(escenicContent.getEscenicLocation());
						pictureRelLink.setThumbnail(escenicImage.getThumbnailUrl());
						pictureRelLink.setRel("related");
						pictureRelLink.setState("published");
						pictureRelLink.setType("application/atom+xml; type=entry");
						pictureRelLink.setIdentifier(escenicContent.getEscenicId());
						links.add(pictureRelLink);


//
					}


				}
			}
		}
			return links;

	}

	public List<Link> generateLinks(EscenicContent escenicContent) {
		List<Link> links = new ArrayList<>();


		//TODO
		//TODO
		//TODO we need to be able to map things correctly here!!
		if (escenicContent != null) {

			if (escenicContent instanceof EscenicImage) {
				EscenicImage escenicImage = (EscenicImage)escenicContent;


				Link thumbnailLink = new Link();
				thumbnailLink.setHref(escenicImage.getThumbnailUrl());
				thumbnailLink.setRel("thumbnail");
				thumbnailLink.setType("image/png");
				links.add(thumbnailLink);




				if (escenicImage.isTopElement()) {
					//TODO does this happen automatically? Do we define pictureRel along side the inline relations?
					Link pictureRelLink = new Link();
					Payload payload1 = new Payload();
					List<Field> fields1 = new ArrayList<>();
					fields1.add(createField("title", escenicImage.getTitle(), null, null));
					//TODO
					fields1.add(createField("caption", "test", null, null));
					payload1.setField(fields1);
					payload1.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-summary/picture");
					pictureRelLink.setPayload(payload1);
					pictureRelLink.setGroup("pictureRel");
					pictureRelLink.setHref(escenicContent.getEscenicLocation());
					pictureRelLink.setThumbnail(escenicImage.getThumbnailUrl());
					pictureRelLink.setRel("related");
					pictureRelLink.setState("published");
					pictureRelLink.setType("application/atom+xml; type=entry");
					pictureRelLink.setIdentifier(escenicContent.getEscenicId());
					links.add(pictureRelLink);
				}

				if (escenicImage.isInlineElement()) {
					Link link = new Link();
					Payload payload = new Payload();
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", escenicImage.getTitle(), null, null));
					fields.add(createField("caption", escenicImage.getTitle(), null, null));
					link.setGroup("com.escenic.inlineRelations");
					link.setThumbnail(escenicImage.getThumbnailUrl());
					payload.setField(fields);
					payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-summary/picture");
					link.setHref(escenicContent.getEscenicLocation());
					link.setType("application/atom+xml; type=entry");
					link.setRel("related");
					link.setState("published");
					link.setPayload(payload);
					link.setIdentifier(escenicContent.getEscenicId());

					link.setTitle(escenicImage.getTitle());
					links.add(link);
				}

			} else if (escenicContent instanceof EscenicGallery) {
				EscenicGallery escenicGallery = (EscenicGallery)escenicContent;
				Link thumbnailLink = new Link();
				thumbnailLink.setHref(escenicGallery.getThumbnailUrl());
				thumbnailLink.setRel("thumbnail");
				thumbnailLink.setType("image/png");
				links.add(thumbnailLink);

				if (escenicGallery.isTopElement()) {
					//TODO does this happen automatically? Do we define pictureRel along side the inline relations?
					Link pictureRelLink = new Link();
					Payload payload1 = new Payload();
					List<Field> fields1 = new ArrayList<>();
					fields1.add(createField("title", escenicGallery.getTitle(), null, null));
					//TODO
					fields1.add(createField("caption", escenicGallery.getTitle(), null, null));
					payload1.setField(fields1);
					payload1.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-summary/gallery");
					pictureRelLink.setPayload(payload1);
					pictureRelLink.setGroup("pictureRel");
					pictureRelLink.setHref(escenicContent.getEscenicLocation());
					pictureRelLink.setThumbnail(escenicGallery.getThumbnailUrl());
					pictureRelLink.setRel("related");
					pictureRelLink.setState("published");
					pictureRelLink.setType("application/atom+xml; type=entry");
					pictureRelLink.setIdentifier(escenicContent.getEscenicId());
					links.add(pictureRelLink);
				}

				if (escenicContent.isInlineElement()) {
					Link link = new Link();
					Payload payload = new Payload();
					List<Field> fields = new ArrayList<>();
					fields.add(createField("title", escenicGallery.getTitle(), null, null));
					fields.add(createField("caption", "test", null, null));
					link.setGroup("com.escenic.inlineRelations");
					link.setThumbnail(escenicGallery.getThumbnailUrl());
					payload.setField(fields);
					payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-summary/gallery");
					link.setHref(escenicContent.getEscenicLocation());
					link.setType("application/atom+xml; type=entry");
					link.setRel("related");
					link.setState("published");
					link.setPayload(payload);
					link.setIdentifier(escenicContent.getEscenicId());

					link.setTitle(escenicGallery.getTitle());
					links.add(link);
				}

			} else if (escenicContent instanceof EscenicEmbed) {
				EscenicEmbed escenicEmbed = (EscenicEmbed)escenicContent;
				Link link = new Link();
				Payload payload = new Payload();
				List<Field> fields = new ArrayList<>();
				fields.add(createField("title", "adm embed", null, null));
				link.setGroup("com.escenic.inlineRelations");
				payload.setField(fields);
				payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-summary/socialembed");
				link.setHref(escenicEmbed.getEscenicLocation());
				link.setType("application/atom+xml; type=entry");
				link.setRel("related");
				link.setState("published");
				link.setPayload(payload);
				link.setIdentifier(escenicEmbed.getEscenicId());

				link.setTitle("adm embed");
				links.add(link);
			}
		}

		return links;

	}

	private String cleanHTMLToText(String articleField) {

		if (articleField == null) return null;

		Document doc = Jsoup.parse (articleField);

		doc = new Cleaner(Whitelist.none()).clean(doc);

		return doc.body().text();

	}

	private String cleanHTMLToXHTML(String text) {
		if (text == null) return null;

		Document doc = Jsoup.parse (text);

		removeComments(doc);

		doc.outputSettings().escapeMode(Entities.EscapeMode.xhtml);

		return doc.body().html();
	}

	private static String escapeHtml(String text) {
		return StringEscapeUtils.escapeHtml(text);
	}

	private void removeComments(Node node) {
		for (int i = 0; i < node.childNodes().size();) {
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

	public static Object extractContentBean(ContentId contentId, ContentManager contentManager) {
		ContentResult<OneContentBean> contentCr =  checkAndExtractContentResult(contentId, contentManager);
//			Object contentResultObject =
//			if (contentResultObject != null) {
//				if (contentResultObject instanceof ContentResult) {
//					contentCr = (ContentResult) contentResultObject;
//				}
//			}
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

	protected static ContentResult checkAndExtractContentResult(ContentId contentId, ContentManager contentManager) {

		ContentResult<OneContentBean> contentCr = null;
		ContentVersionId contentVersionId = null;
		try {
			contentVersionId = contentManager.resolve(contentId, Subject.NOBODY_CALLER);
		} catch (StorageException e) {
			log.error("error occurred when resolving content id: " + contentId + e);
		}

		if (contentVersionId != null) {
			contentCr = contentManager.get(contentVersionId, null, OneContentBean.class, null, Subject.NOBODY_CALLER);
		} else {
			//error here about contentversionid

		}

		if (contentCr != null && contentCr.getStatus().isSuccess()) {
			return contentCr;
		} else {
			//throw an error?
			throw new RuntimeException("Retrieing content failed: " + contentCr.getStatus());
		}


	}



}
