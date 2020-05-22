package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.util.DamUtils;
import com.atex.onecms.content.*;
import com.atex.onecms.content.repository.StorageException;
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
import java.util.logging.Level;

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
//	seda://unpublishFromEscenic
	protected static final String PUBLISH_ACTION = "post-to-escenic";
	protected static final String UNPUBLISH_ACTION = "unpublish-from-escenic";
	protected final Logger log = LoggerFactory.getLogger(getClass());

	private HttpFileServiceClient httpFileServiceClient;
	private String imageServiceUrl;
	private static final String IMAGE_SERVICE_URL_FALLBACK = "http://localhost:8080";

	@Override
	public void process(final Exchange exchange) throws Exception {
		Response finalResponse = null;

		log.debug("EscenicProcessor - start work");
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

			if (StringUtils.isNotBlank(action)) {
				if(StringUtils.equalsIgnoreCase(action, PUBLISH_ACTION)) {
					System.out.println("IT WAS PUBLISH");
				} else if(StringUtils.equalsIgnoreCase(action, UNPUBLISH_ACTION)) {
					System.out.println("IT WAS UNPUBLISH");
				}


			}

			String contentIdString;
			if (message.getBody() instanceof String) {
				contentIdString = getContentId(exchange);
			} else {
				contentIdString = exchange.getIn().getHeader("contentId", ContentId.class).getKey();
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
			finalResponse = Response.serverError().entity(e.getCause() + ": " + e.getMessage()).build();
		} finally {

			if (finalResponse == null) {
				finalResponse = Response.serverError().entity("An Unknown error occurred while processing").build();
			}
//			exchange.getOut().setBody("escenicId");
			exchange.getOut().setHeader("response",finalResponse);
			System.out.println("Sending back a response entity\n: " + finalResponse.getEntity().toString());
			log.debug("Escenic processor - end work");
		}

	}

//	private String extractSectionId(ContentResult cr) {
//
//
//		if (StringUtils.isBlank(escenicConfig.getWebSectionDimension())) {
//			throw new RuntimeException("Web section dimension not specified in the configuration. Unable to proceed");
//		}
//
//		//TODO for now we'll always return a test value;
//		return "2";
//
////		if (cr != null && cr.getStatus().isSuccess()) {
////			MetadataInfo metadataInfo = (MetadataInfo) cr.getContent().getAspectData(MetadataInfo.ASPECT_NAME);
////			if (metadataInfo != null) {
////				Metadata metadata = metadataInfo.getMetadata();
////				if (metadata != null) {
////					Dimension dim = metadata.getDimensionById(escenicConfig.getWebSectionDimension());
////					if (dim != null) {
////						List<Entity> entites =  dim.getEntities();
////						//TODO
//////						"dimensions": [
//////						{
//////							"name": "Web Section",
//////							"id": "department.categorydimension.inm",
//////							"entities": [
//////							{
//////								"name": "Belfast Telegraph",
//////								"id": "Belfast Telegraph",
//////								"attributes": [],
//////								"entities": [
//////								{
//////									"name": "Sunday Life",
//////									"id": "Belfast Telegraph.Sunday Life",
//////									"attributes": [],
//////									"entities": [
//////									{
//////										"name": "Bar Person Of The Year Awards",
//////										"id": "Belfast Telegraph.Sunday Life.Bar Person Of The Year Awards",
//////										"attributes": [],
//////										"entities": [],
//////										"childrenOmitted": false,
//////										"localizations": {}
//////									}
//////                      ],
//////									"childrenOmitted": false,
//////									"localizations": {}
//////								}
//////                  ],
//////								"childrenOmitted": false,
//////								"localizations": {}
//////							}
//////              ],
//////							"enumerable": true,
//////							"localizations": {}
//////						},
////
////
////					}
////
////				} else {
////					throw new RuntimeException("Failed to extract section id due to failure reading metadata");
////				}
////
////			}
////
//
////		}
//
//
//	}
//
//	private EscenicContent processTopElement(ContentResult<Object> cr, ContentManager contentManager, DamEngagementUtils utils, PolicyCMServer cmServer, OneArticleBean article, Entry existingEntry, List<EscenicContent> escenicContentList, String sectionId) {
//
//		if (article != null && article.getTopElement() != null) {
//			ContentId contentId = article.getTopElement();
//
//
//			/**
//			 * This is to prevent pushing the image twice if it's both in body & set as top element as well
//			 */
//			if (contentId != null) {
//				if (escenicContentList != null) {
//					for (EscenicContent escenicContent : escenicContentList) {
//						if (escenicContent != null && escenicContent.getOnecmsContentId() != null) {
//							if (StringUtils.equalsIgnoreCase(IdUtil.toIdString(escenicContent.getOnecmsContentId()), IdUtil.toIdString(contentId))) {
//								//they're the same, therefore update the escenic content with the flag and return and avoid processing
//								if (escenicContent instanceof EscenicImage) {
//									EscenicImage img = (EscenicImage) escenicContent;
//
//									if (img != null) {
//										img.setTopElement(true);
//										return null;
//									}
//								}
//							}
//						}
//					}
//				}
//			}
//
//			ContentResult<OneContentBean> contentCr = escenicUtils.checkAndExtractContentResult(contentId, contentManager);
//
//			if (contentCr != null && contentCr.getStatus().isSuccess()) {
//				Object contentData = escenicUtils.extractContentBean(contentCr);
//				if (contentData != null) {
//					if (contentData instanceof OneImageBean) {
//						OneImageBean oneImageBean = (OneImageBean) contentData;
//
//						if (oneImageBean != null) {
//							EscenicImage escenicImage = new EscenicImage();
//							escenicImage.setOnecmsContentId(contentId);
//							escenicImage.setTopElement(true);
//							escenicImage.setInlineElement(false);
//
//							String existingEscenicLocation = null;
//							try {
//								existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
//							} catch (CMException e) {
//								e.printStackTrace();
//							}
//
//							boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);
//
//							Entry escenicImageEntry = null;
//							String existingEscenicId = null;
//							if (isUpdate) {
//								//load image + merge it with existing image and send an update? + process the response?
//								existingEscenicId = extractIdFromLocation(existingEscenicLocation);
//								if (StringUtils.isNotEmpty(existingEscenicId)) {
//
//									try {
//										escenicImageEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation, escenicConfig);
//									} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
//										e.printStackTrace();
//									}
//								}
//							}
//							CloseableHttpResponse response = null;
//							try {
//								response = escenicUtils.processImage(contentCr, escenicImageEntry, existingEscenicLocation, cmServer, escenicConfig, escenicImage, sectionId);
//							} catch (IOException | CMException | URISyntaxException e) {
//								e.printStackTrace();
//							}
//
//							EngagementDesc engagementDesc = null;
//							try {
//								engagementDesc = evaluateResponse(contentId, existingEscenicLocation, existingEscenicId, false, response, utils);
//							} catch (CMException e) {
//								e.printStackTrace();
//							}
//
//							String escenicLocation = null;
//							String escenicId = null;
//
//							if (engagementDesc != null) {
//
//								if (StringUtils.isNotBlank(engagementDesc.getAppPk())) {
//									escenicId = engagementDesc.getAppPk();
//								}
//
//								if (engagementDesc.getAttributes() != null) {
//									for (EngagementElement element : engagementDesc.getAttributes()) {
//										if (element != null) {
//											if (StringUtils.equalsIgnoreCase(element.getName(), "location")) {
//												escenicLocation = element.getValue();
//											}
//										}
//									}
//								}
//
//							} else {
//								escenicLocation = existingEscenicLocation;
//								escenicId = existingEscenicId;
//
//							}
//
//							escenicImage.setEscenicId(escenicId);
//							escenicImage.setEscenicLocation(escenicLocation);
//							//TODO hack to change the URL from what's provided to a thumbnail url --> alternative is to query for content and read it.
//							escenicImage.setThumbnailUrl(escenicLocation.replaceAll("escenic/content", "thumbnail/article"));
//							List<Link> links = escenicUtils.generateLinks(escenicImage, escenicConfig);
//							escenicImage.setLinks(links);
//
//
//							return escenicImage;
//
//						}
//
//
//					} else if (contentData instanceof DamCollectionAspectBean) {
//						DamCollectionAspectBean collectionAspectBean = (DamCollectionAspectBean) contentData;
//						if (collectionAspectBean != null) {
//							String existingEscenicLocation = null;
//							try {
//								existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
//							} catch (CMException e) {
//								//TODO
//								e.printStackTrace();
//							}
//							boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);
//							EscenicGallery escenicGallery = new EscenicGallery();
//							escenicGallery.setOnecmsContentId(contentId);
//							escenicGallery.setTopElement(true);
//							escenicGallery.setInlineElement(false);
//							List<EscenicContent> collectionEscenicItems = escenicGallery.getContentList();
//							String existingEscenicId = null;
//							Entry existingGalleryEntry = null;
//							if (isUpdate) {
//								existingEscenicId = extractIdFromLocation(existingEscenicLocation);
//								if (StringUtils.isNotEmpty(existingEscenicId)) {
//									try {
//										existingGalleryEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation, escenicConfig);
//									} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
//										e.printStackTrace();
//									}
//								}
//							} else {
//								escenicGallery.setOnecmsContentId(contentId);
//								List<ContentId> collectionItems = collectionAspectBean.getContents();
//
//								for (ContentId id : collectionItems) {
//
//									ContentResult<Object> item = escenicUtils.checkAndExtractContentResult(id, contentManager);
//									if (item != null) {
//										Object bean = escenicUtils.extractContentBean(item);
//
//										if (bean != null && bean instanceof OneImageBean) {
//											EscenicImage escenicImage = new EscenicImage();
//											escenicImage.setOnecmsContentId(id);
//											escenicImage = processImage(id, escenicImage, utils, collectionEscenicItems, sectionId);
//
//											if (escenicImage != null) {
//												collectionEscenicItems.add(escenicImage);
//											} else {
//												log.error("Something went wrong while processing an image with id: " + IdUtil.toIdString(id));
//											}
//
//										}
//									}
//								}
//							}
//
//							List<CloseableHttpResponse> responses = escenicUtils.processGallery(collectionAspectBean, existingGalleryEntry, escenicGallery, escenicConfig, sectionId);
//
//							for (CloseableHttpResponse response : responses) {
//								int statusCode = response.getStatusLine().getStatusCode();
//								if (statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NO_CONTENT) {
//									String escenicLocation = null;
//									try {
//										escenicLocation = retrieveEscenicLocation(response);
//									} catch (IOException | FailedToExtractLocationException e) {
//										e.printStackTrace();
//									}
//									String escenicId = extractIdFromLocation(escenicLocation);
//									final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
//									escenicGallery.setEscenicId(escenicId);
//									escenicGallery.setEscenicLocation(escenicLocation);
//									//TODO don't like this rly...
//									escenicGallery.setThumbnailUrl(escenicLocation.replaceAll("escenic/content", "thumbnail/article"));
//									escenicGallery.setTitle(collectionAspectBean.getHeadline());
//									List<Link> links = escenicUtils.generateLinks(escenicGallery, escenicConfig);
//									escenicGallery.setLinks(links);
//									//atempt to update the engagement
//									try {
//										processEngagement(contentId, engagement, existingEscenicLocation, utils);
//									} catch (CMException e) {
//										//TODO throw specific exceptiuon here
//									}
//
//									return escenicGallery;
//								} else {
//									log.error("The server returned : " + response.getStatusLine() + " when attempting to sendimage id: " + contentId);
//								}
//							}
//						}
//					}
//				}
//			} else {
//				//content result blank or failed
//				log.error("Failed to retrieve content result");
//			}
//		}
//
//		return null;
//	}
//
//	private EngagementDesc evaluateResponse(ContentId contentId, String existingEscenicLocation, String existingEscenicId, boolean updateWebAttribute, CloseableHttpResponse response, DamEngagementUtils utils) throws CMException {
//		if (response != null) {
//			String escenicId = null;
//			String escenicLocation = null;
//			int statusCode = response.getStatusLine().getStatusCode();
//			if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
//
//				try {
//					escenicLocation = retrieveEscenicLocation(response);
//				} catch (IOException | FailedToExtractLocationException e) {
//					e.printStackTrace();
//				}
//				escenicId = extractIdFromLocation(escenicLocation);
//				if (StringUtils.isBlank(escenicId)) {
//					throw new RuntimeException("Extracted escenic id is blank for location: " + escenicLocation);
//				}
//
//				if (updateWebAttribute) {
//					try {
//						updateWebAttribute(contentId, escenicId);
//					} catch (ContentModifiedException e) {
//						e.printStackTrace();
//					}
//				}
//
//				final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
//				processEngagement(contentId, engagement, null, utils);
//				return engagement;
//
//			} else if (statusCode == HttpStatus.SC_NO_CONTENT) {
//				//ensure the status is online
//				if (updateWebAttribute) {
//					if (StringUtils.isNotBlank(existingEscenicId)) {
//						try {
//							updateWebAttribute(contentId, existingEscenicId);
//						} catch (ContentModifiedException e) {
//							e.printStackTrace();
//						}
//					} else {
//						log.warn("Unable to update the web attribute due to lack of existing escenic id");
//					}
//				}
//
//				if (StringUtils.isNotBlank(existingEscenicLocation) && StringUtils.isNotBlank(existingEscenicId)) {
//					final EngagementDesc engagement = createEngagementObject(existingEscenicId, existingEscenicLocation, getCurrentCaller());
//					processEngagement(contentId, engagement, existingEscenicLocation, utils);
//				}
//
//			} else if (statusCode == HttpStatus.SC_CONFLICT) {
//				//todo needed?
//				//todo what other codes we need to handle?
//
//				// we'll probably just fail here and ask the user to retry.
//
//			}
//		}
//		return null;
//	}
//
//	private EscenicEmbed processSocialEmbed(CustomEmbedParser.SmartEmbed embed, DamEngagementUtils utils, ContentId articleContentId, String sectionId) throws IOException, URISyntaxException {
//		ContentId contentId = null;
//		if (embed != null && embed.getContentId() != null) {
//			contentId = embed.getContentId();
//		}
//		/**
//		 * TODO issue here is that we do not currently store the embeds...
//		 *
//		 * create it as a content ? -> so that we can then update the engagmenet.
//		 * Do we need to update the body of the article with updated html that contains onecms-id of embed object for further processing?
//		 */
////		for (CustomEmbedParser.SocialEmbed embed : socialEmbeds) {
//
//			EscenicEmbed escenicEmbed = new EscenicEmbed();
//			if (contentId != null) {
//				//TODO
//				//NOT NECESSARILY means we're dealing with embed that has already been published...
//				ContentResult<DamEmbedAspectBean> embedCr = null;
//				ContentVersionId contentVersionId = contentManager.resolve(contentId, Subject.NOBODY_CALLER);
//				if (contentVersionId != null) {
//					embedCr = contentManager.get(contentVersionId, null, DamEmbedAspectBean.class, null, Subject.NOBODY_CALLER);
//				}
//
//				if (embedCr != null && embedCr.getStatus().isSuccess()) {
//					//check if the enagement on the image exists?
//					//we'll need to detect the filename used? just in case the user removes and image and uploads a different one for the same desk object..
//					//this could cause a problem on the escenic side if the image was already uploaded (the binary file)
//					String existingEscenicLocation = null;
//					try {
//						existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
//					} catch (CMException e) {
//						e.printStackTrace();
//					}
//
//					String escenicId = extractIdFromLocation(existingEscenicLocation);
//					if (StringUtils.isNotEmpty(escenicId)) {
//						escenicEmbed.setEscenicLocation(existingEscenicLocation);
//						escenicEmbed.setEscenicId(escenicId);
//						escenicEmbed.setEmbedCode(embed.getEmbedCode());
//						escenicEmbed.setEmbedUrl(embed.getEmbedUrl());
//						contentId = createOnecmsEmbed(escenicEmbed );
//						escenicEmbed.setOnecmsContentId(contentId);
//						List<Link> links = escenicUtils.generateLinks(escenicEmbed, escenicConfig);
//						escenicEmbed.setLinks(links);
//					}
//				}
//			} else {
//				//brand new embed that does not exist in escenic
//				List<CloseableHttpResponse> responses;
//				responses = escenicUtils.processEmbed(embed, cmServer, escenicConfig, escenicEmbed, sectionId);
//				for (CloseableHttpResponse response : responses) {
//					int statusCode = response.getStatusLine().getStatusCode();
//					//|| statusCode == HttpStatus.SC_NO_CONTENT
//					if (statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_OK) {
//						String escenicLocation = null;
//						try {
//							escenicLocation = retrieveEscenicLocation(response);
//						} catch (FailedToExtractLocationException e) {
//							e.printStackTrace();
//						}
//						String escenicId = extractIdFromLocation(escenicLocation);
//						final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
//						escenicEmbed.setEscenicLocation(escenicLocation);
//						escenicEmbed.setEscenicId(escenicId);
//						escenicEmbed.setEmbedCode(embed.getEmbedCode());
//						escenicEmbed.setEmbedUrl(embed.getEmbedUrl());
//						contentId = createOnecmsEmbed(escenicEmbed );
//						escenicEmbed.setOnecmsContentId(contentId);
//						List<Link> links = escenicUtils.generateLinks(escenicEmbed, escenicConfig);
//						escenicEmbed.setLinks(links);
//
//						try {
//							processEngagement(contentId, engagement, null, utils);
//						} catch (CMException e) {
//							e.printStackTrace();
//						}
//
//
//					} else {
//						log.error("The server returned : " + response.getStatusLine() + " when attempting to send embed id: " + escenicEmbed.getOnecmsContentId());
//					}
//				}
//			}
//
//		return escenicEmbed;
//	}
//
//	/**
//	 * responsible for creating dummy onecms Embeds for the purpose of storing engagements
//	 * @param escenicEmbed
//	 */
//	private ContentId createOnecmsEmbed(EscenicEmbed escenicEmbed) {
//		escenicEmbed.getEscenicId();
//		escenicEmbed.getEmbedCode();
//		escenicEmbed.getEscenicLocation();
//
//		DamEmbedAspectBean embedAspectBean = new DamEmbedAspectBean();
//		embedAspectBean.setHtml(escenicEmbed.getEmbedCode());
//		//todo parse the name based on the url...
//		embedAspectBean.setName(escenicEmbed.getEscenicId());
//
//		ContentWrite<DamEmbedAspectBean> content  = new ContentWriteBuilder<DamEmbedAspectBean>()
//			.type(DamEmbedAspectBean.ASPECT_NAME)
//			.mainAspectData(embedAspectBean)
//			.buildCreate();
//
//		if (content != null) {
//			ContentResult<DamEmbedAspectBean> cr = contentManager.create(content, SubjectUtil.fromCaller(getCurrentCaller()));
//			return cr.getContent().getId().getContentId();
//
//		} else {
//			throw new RuntimeException("failed to create DamEmbedAspectBean");
//		}
//	}
//
//	private List<EscenicContent> processSmartEmbeds(ContentResult<Object> cr, ContentManager contentManager, DamEngagementUtils utils, PolicyCMServer cmServer, OneArticleBean article, Entry entry, String sectionId) throws CMException, IOException, URISyntaxException {
//		log.debug("Processing smart embeds");
//		List<EscenicContent> escenicContentList = new ArrayList<>();
//		if (cr.getStatus().isSuccess()) {
//			List<CustomEmbedParser.SmartEmbed> embeds = escenicUtils.processEmbeds(article.getBody().getText());
//			Content c = cr.getContent();
//			if (c != null) {
//
//				List<Link> links = null;
//				if (entry != null && entry.getLink() != null) {
//					links = entry.getLink();
//				}
//				//at this stage we assume that content does not exist in escenic.. wrong assumption?
//				for (CustomEmbedParser.SmartEmbed embed : embeds) {
//					if (embed != null) {
//
//						if (StringUtils.isNotEmpty(embed.getObjType())) {
//							switch (embed.getObjType()) {
//								case EscenicImage.IMAGE_TYPE:
//									if (embed.getContentId() != null) {
//										EscenicImage escenicImage = new EscenicImage();
//										escenicImage.setOnecmsContentId(embed.getContentId());
//										escenicImage = processImage(embed.getContentId(), escenicImage, utils, escenicContentList, sectionId);
//
//										if (escenicImage != null) {
//											escenicContentList.add(escenicImage);
//										} else {
//											log.error("Something went wrong while processing an image with id: " + IdUtil.toIdString(embed.getContentId()));
//										}
//									} else {
//										log.warn("Unable to process an inline image as the onecms id was not found in embeded text");
//									}
//
//
//									break;
//
//								case EscenicGallery.GALLERY_TYPE:
//									if (embed.getContentId() != null) {
//										EscenicGallery escenicGallery = processGallery(embed.getContentId(), embed, utils, sectionId);
//										escenicContentList.add(escenicGallery);
//									}
//
//									break;
//
//								case EscenicEmbed.SOCIAL_EMBED_TYPE:
//									//special case - we won't be sending any updates to social embeds I guess?
//									//So in here, if the entry exists, you can reuse what's already on the entry.
//									//of course if the embed escenic id matches what's in the link, otherwise create a new one.
//									if (entry == null) {
//										EscenicEmbed escenicEmbed = processSocialEmbed(embed, utils, cr.getContent().getId().getContentId(), sectionId);
//										escenicContentList.add(escenicEmbed);
//									} else if (links != null) {
//										boolean found = false;
//										for (Link link : links) {
//											if (StringUtils.isNotEmpty(embed.getEscenicId()) && StringUtils.equalsIgnoreCase(link.getIdentifier(), embed.getEscenicId())) {
//												EscenicEmbed escenicEmbed = new EscenicEmbed();
//												escenicEmbed.setEscenicLocation(link.getHref());
//												escenicEmbed.setEscenicId(link.getIdentifier());
//												escenicEmbed.setEmbedCode(embed.getEmbedCode());
//												escenicEmbed.setEmbedUrl(embed.getEmbedUrl());
//												escenicEmbed.setLinks(Arrays.asList(link));
//												escenicContentList.add(escenicEmbed);
//												found = true;
//												break;
//											}
//										}
//
//										if (!found) {
//											//we're dealing with new embed instead..
//											EscenicEmbed escenicEmbed = processSocialEmbed(embed, utils, cr.getContent().getId().getContentId(), sectionId);
//											escenicContentList.add(escenicEmbed);
//										}
//									}
//
//									break;
//							}
//						}
//					}
//				}
//			}
//		}
//
//		return escenicContentList;
//	}
//
//	private ContentResult<Object> updateArticleBodyWithOnecmsIds(List<EscenicContent> escenicContentList, ContentResult<Object> cr) {
//		if (cr.getStatus().isSuccess()) {
//			Content<Object> articleBeanContent = cr.getContent();
//			if (articleBeanContent != null) {
//				OneArticleBean article = (OneArticleBean) articleBeanContent.getContentData();
//				if (article != null) {
//
//						if (article.getBody() != null) {
//							article.getBody().setText(escenicUtils.addOnecmsIdToSocialEmbeds(article.getBody().getText(), escenicContentList));
//						}
//
//
//					ContentWrite<CustomArticleBean> content  = new ContentWriteBuilder<CustomArticleBean>()
//						.type(CustomArticleBean.ASPECT_NAME)
//						.mainAspectData((CustomArticleBean)article)
//						.origin(cr.getContentId())
//						.buildUpdate();
//
//					if (content != null) {
//						try {
//							return contentManager.update(cr.getContent().getId().getContentId(), content, SubjectUtil.fromCaller(getCurrentCaller()));
//						} catch (ContentModifiedException e) {
//							e.printStackTrace();
//						}
//					} else {
//						throw new RuntimeException("Failed to update article with onecms ids for social embeds");
//					}
//				}
//			}
//		}
//		return null;
//
//	}
//
//	private EscenicGallery processGallery(ContentId contentId, CustomEmbedParser.SmartEmbed embed, DamEngagementUtils utils, String sectionId) {
//
//		EscenicGallery escenicGallery = new EscenicGallery();
//		List<EscenicContent> collectionEscenicItems = escenicGallery.getContentList();
//
//		ContentResult collectionCr = escenicUtils.checkAndExtractContentResult(contentId, contentManager);
//
//		if (collectionCr != null && collectionCr.getStatus().isSuccess()) {
//			Object contentBean = escenicUtils.extractContentBean(collectionCr);
//			if (contentBean != null && contentBean instanceof DamCollectionAspectBean) {
//
//				DamCollectionAspectBean collectionAspectBean = (DamCollectionAspectBean) contentBean;
//
//				if (collectionAspectBean != null) {
//
//					String existingEscenicLocation = null;
//					try {
//						existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
//					} catch (CMException e) {
//						//TODO
//						e.printStackTrace();
//					}
//					boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);
//
//					String existingEscenicId = null;
//					Entry existingGalleryEntry = null;
//					if (isUpdate) {
//						existingEscenicId = extractIdFromLocation(existingEscenicLocation);
//						if (StringUtils.isNotEmpty(existingEscenicId)) {
//
//							try {
//								existingGalleryEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation, escenicConfig);
//							} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
//								e.printStackTrace();
//							}
//						}
//					} else {
//
//						escenicGallery.setOnecmsContentId(contentId);
//						List<ContentId> collectionItems = collectionAspectBean.getContents();
//
//						for (ContentId id : collectionItems) {
//
//							ContentResult item = escenicUtils.checkAndExtractContentResult(id, contentManager);
//
//							if (item != null && item.getStatus().isSuccess()) {
//								Object bean = escenicUtils.extractContentBean(item);
//
//								if (bean != null && bean instanceof OneImageBean) {
//									EscenicImage escenicImage = new EscenicImage();
//									escenicImage.setOnecmsContentId(id);
//									escenicImage = processImage(id, escenicImage, utils, collectionEscenicItems, sectionId);
//									if (escenicImage != null) {
//										collectionEscenicItems.add(escenicImage);
//									} else {
//										log.error("Something went wrong while processing an image with id: " + IdUtil.toIdString(id));
//									}
//								}
//							}
//						}
//					}
//					List<CloseableHttpResponse> responses = escenicUtils.processGallery(collectionAspectBean, existingGalleryEntry, escenicGallery, escenicConfig, sectionId);
//
//					for (CloseableHttpResponse response : responses) {
//						int statusCode = response.getStatusLine().getStatusCode();
//						if (statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NO_CONTENT) {
//							String escenicLocation = null;
//							try {
//								escenicLocation = retrieveEscenicLocation(response);
//							} catch (IOException | FailedToExtractLocationException e) {
//								e.printStackTrace();
//							}
//							String escenicId = extractIdFromLocation(escenicLocation);
//							final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
//							escenicGallery.setEscenicId(escenicId);
//							escenicGallery.setEscenicLocation(escenicLocation);
//							escenicGallery.setThumbnailUrl(escenicLocation.replaceAll("escenic/content", "thumbnail/article"));
//							escenicGallery.setTitle(collectionAspectBean.getHeadline());
//							List<Link> links = escenicUtils.generateLinks(escenicGallery, escenicConfig);
//							escenicGallery.setLinks(links);
//							//atempt to update the engagement
//							try {
//								processEngagement(contentId,engagement,existingEscenicLocation, utils);
//							} catch (CMException e) {
//								//TODO throw specific exceptiuon here
//							}
//						} else {
//							log.error("The server returned : " + response.getStatusLine() + " when attempting to sendimage id: " + contentId);
//						}
//
//					}
//
//				}
//
//			}
//		}
//		return escenicGallery;
//
//	}
//
//	private EscenicImage processImage(ContentId contentId, EscenicImage escenicImage, DamEngagementUtils utils, List<EscenicContent> escenicContentList, String sectionId) {
//
//		ContentResult imgCr =  escenicUtils.checkAndExtractContentResult(contentId, contentManager);
//
//		if (imgCr != null && imgCr.getStatus().isSuccess()) {
//			//check if the enagement on the image exists?
//			//we'll need to detect the filename used? just in case the user removes and image and uploads a different one for the same desk object..
//			//this could cause a problem on the escenic side if the image was already uploaded (the binary file)
//			String existingEscenicLocation = null;
//			try {
//				existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
//			} catch (CMException e) {
//				e.printStackTrace();
//			}
//
//			boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);
//			List<CloseableHttpResponse> responses = new ArrayList<>();
//
//			Entry escenicImageEntry = null;
//			String existingEscenicId = null;
//			if (isUpdate) {
//				//load image + merge it with existing image and send an update? + process the response?
//				existingEscenicId = extractIdFromLocation(existingEscenicLocation);
//				if (StringUtils.isNotEmpty(existingEscenicId)) {
//
//					try {
//						escenicImageEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation, escenicConfig);
//					} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
//						e.printStackTrace();
//					}
//				}
//			}
//			CloseableHttpResponse response = null;
//			try {
//				response = escenicUtils.processImage(imgCr, escenicImageEntry, existingEscenicLocation,  cmServer, escenicConfig, escenicImage, sectionId);
//			} catch (IOException | CMException | URISyntaxException e) {
//				e.printStackTrace();
//			}
//
//
//			if (response != null) {
//				int statusCode = response.getStatusLine().getStatusCode();
//				String escenicLocation = null;
//				String escenicId = null;
//				if (statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_OK ) {
//
//					try {
//						escenicLocation = retrieveEscenicLocation(response);
//					} catch (IOException | FailedToExtractLocationException e) {
//						e.printStackTrace();
//					}
//
//					escenicId = extractIdFromLocation(escenicLocation);
//				} else if (statusCode == HttpStatus.SC_NO_CONTENT) {
//					escenicLocation = existingEscenicLocation;
//					escenicId = existingEscenicId;
//
//				} else {
//					log.error("The server returned : " + response.getStatusLine() + " when attempting to sendimage id: " + contentId);
//					//throw an error here.
//				}
//
//					final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
//					escenicImage.setEscenicId(escenicId);
//					escenicImage.setEscenicLocation(escenicLocation);
//
//					//TODO hack to change the URL from what's provided to a thumbnail url --> alternative is to query for content and read it.
//					escenicImage.setThumbnailUrl(escenicLocation.replaceAll("escenic/content", "thumbnail/article"));
//
//					List<Link> links = escenicUtils.generateLinks(escenicImage, escenicConfig);
//					escenicImage.setLinks(links);
//
//					//atempt to update the engagement
//					try {
//						processEngagement(contentId,engagement,existingEscenicLocation, utils);
//					} catch (CMException e) {
//						e.printStackTrace();
//					}
//					return escenicImage;
//			}
//		}
//		return null;
//	}
//
//
//	private String extractIdFromLocation(String escenicLocation) {
//		String id = null;
//		if (StringUtils.isNotEmpty(escenicLocation)) {
//			id = escenicLocation.substring(escenicLocation.lastIndexOf('/') + 1);
////			http://inm-test-editorial.inm.lan:8081/webservice/escenic/content/250358548
//		}
//
//		return id;
//	}
//
//	private String retrieveEscenicLocation(CloseableHttpResponse result) throws IOException, FailedToExtractLocationException {
//		if (result != null && result.getEntity() != null) {
//			System.out.println(EntityUtils.toString(result.getEntity()));
//
//			Header[] headers = result.getAllHeaders();
//			for (int i = 0; i < headers.length; i++) {
//				Header header = headers[i];
//				System.out.println(header);
//				System.out.println("Header: " + i + " = " + header);
//				if (StringUtils.endsWithIgnoreCase(header.getName(), "Location")) {
//					//todo store the value of the header in the engagement ?
//					return	header.getValue();
//				}
//			}
//
//		} else {
//			throw new FailedToExtractLocationException("Failed to extract escenic location from the response");
//
//		}
//		return null;
//	}
//
//
	private String getContentId(Exchange exchange) {
		String contentIdStr = (String) exchange.getIn().getBody();
		String tidiedContentIdStr = contentIdStr.replace("mutation:", "");
		int lastColon = tidiedContentIdStr.lastIndexOf(':');
		return tidiedContentIdStr.substring(0, lastColon);
	}
//
//	private void processEngagement(ContentId contentId, EngagementDesc engagement, String existingEscenicLocation, DamEngagementUtils utils ) throws CMException {
//
//		//TODO CHECK do we really care about updates?? won't it always point to the same url?
//		if (StringUtils.isNotEmpty(existingEscenicLocation)) {
//			try {
//				utils.updateEngagement(contentId, engagement, SubjectUtil.fromCaller(getCurrentCaller()));
//			} catch (CMException e) {
//				e.printStackTrace();
//			}
//		} else {
//			try {
//				utils.addEngagement(contentId, engagement, SubjectUtil.fromCaller(getCurrentCaller()));
//			} catch (CMException e) {
//				e.printStackTrace();
//			}
//		}
//
//	}
//
//	private EngagementDesc createEngagementObject(String escenicId, String escenicLocation, Caller caller) {
//		if (escenicId == null) {
//			escenicId = "";
//		}
//
//		final EngagementDesc engagement = new EngagementDesc();
//		engagement.setAppType(ESCENIC_APPTYPE);
//		engagement.setAppPk(escenicId);
//		engagement.setUserName("sysadmin");
//		engagement.getAttributes().add(createElement("link", escenicId));
//		engagement.getAttributes().add(createElement("location", escenicLocation != null ? escenicLocation : ""));
//		return engagement;
//	}
//
//	private EngagementElement createElement(final String name, final String value) {
//		final EngagementElement element = new EngagementElement();
//		element.setName(name);
//		element.setValue(value);
//		return element;
//	}
//
//	private String getEscenicIdFromEngagement(final DamEngagementUtils utils, final ContentId contentId) throws CMException {
//		final EngagementAspect engAspect = utils.getEngagement(contentId);
//		if (engAspect != null) {
//			final EngagementDesc engagement = Iterables.getFirst(
//				Iterables.filter(engAspect.getEngagementList(), new Predicate<EngagementDesc>() {
//					@Override
//					public boolean apply(@Nullable final EngagementDesc engagementDesc) {
//						return (engagementDesc != null) && com.polopoly.common.lang.StringUtil.equals(engagementDesc.getAppType(), ESCENIC_APPTYPE);
//					}
//				}), null);
//			if (engagement != null) {
//				for (EngagementElement e : engagement.getAttributes()){
//					if (StringUtils.equalsIgnoreCase(e.getName(), "location")) {
//						return e.getValue();
//					}
//				}
//			}
//		}
//		return null;
//	}
//
//
//	private Caller getCurrentCaller() {
//		return Optional
//			.ofNullable(latestCaller)
//			.orElse(new Caller(new UserId("98")));
//	}

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







//				try {
//					if (com.polopoly.common.lang.StringUtil.isEmpty(DamUtils.getDamUrl())) {
//						imageServiceUrl = IMAGE_SERVICE_URL_FALLBACK;
//						log.warn("desk.config.damUrl is not configured in connection.properties");
//					} else {
//						URL url = new URL(DamUtils.getDamUrl());
//						imageServiceUrl = url.getProtocol() + "://" + url.getHost() + ":" + url.getPort();
//					}
//				} catch (MalformedURLException e) {
//					log.error("Cannot configure the imageServiceUrl: " + e.getMessage());
//					imageServiceUrl = IMAGE_SERVICE_URL_FALLBACK;
//				}
//

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

//	public EmbargoState getEmbargoState(CustomArticleBean content) {
//
//		EmbargoState state = EmbargoState.NOEMBARGO;
//		final CustomArticleBean contentState = content;
//		if (contentState != null) {
//			final TimeState timeState = contentState.getTimeState();
//			if (timeState != null) {
//				final Calendar now = Calendar.getInstance();
//
//				final long onTime = timeState.getOntime();
//				final long offTime = timeState.getOfftime();
//
//				if (offTime > 0) {
//					final Calendar off = Calendar.getInstance();
//					off.setTimeInMillis(offTime);
//					if (off.before(now)) {
//						return EmbargoState.TIMEOFF_PASSED;
//					}
//					state = EmbargoState.EMBARGOED;
//				}
//				if (onTime > 0) {
//					final Calendar on = Calendar.getInstance();
//					on.setTimeInMillis(onTime);
//					if (on.before(now)) {
//						return EmbargoState.TIMEON_PASSED;
//					}
//					state = EmbargoState.EMBARGOED;
//				}
//			}
//		}
//		return state;
//	}
//
//	public WFContentStatusAspectBean updateAttributes(CustomArticleBean bean, WFContentStatusAspectBean wfStatus){
//
//		if(wfStatus != null &&  wfStatus.getStatus() != null){
//			EmbargoState embargoState = getEmbargoState(bean);
//			//clear attributes
//			wfStatus.getStatus().getAttributes().clear();
//			switch (embargoState) {
//				case TIMEOFF_PASSED:
//					wfStatus.getStatus().getAttributes().add(STATUS_ATTR_UNPUBLISHED);
//					break;
//				case EMBARGOED:
//					wfStatus.getStatus().getAttributes().add(STATUS_ATTR_EMBARGO);
//					break;
//				case TIMEON_PASSED:
//				default:
//					wfStatus.getStatus().getAttributes().add(STATUS_ATTR_ONLINE);
//
//			}
//		}
//
//		return wfStatus;
//	}
//
//	private void updateWebAttribute(ContentId contentId, String escenicId) throws ContentModifiedException {//change code here to load content again?
//		ContentVersionId latestVersion = this.contentManager.resolve(contentId, Subject.NOBODY_CALLER);
//
//		ContentResult<OneContentBean> cr = contentManager.get(latestVersion, null, OneContentBean.class, null, Subject.NOBODY_CALLER);
//		if (cr != null && cr.getStatus().isSuccess()) {
//			if (cr.getContent() != null) {
//				Content content = cr.getContent();
//				OneArticleBean articleBean = (OneArticleBean) cr.getContent().getContentData();
//				WFContentStatusAspectBean status = (WFContentStatusAspectBean) content.getAspectData(WFContentStatusAspectBean.ASPECT_NAME);
//				if (status != null && articleBean != null) {
////					WFStatusBean onlineStatus = new WebStatusUtils(contentManager).getStatusById("online");
//					WFStatusBean onlineStatus = status.getStatus();
//					if (onlineStatus != null) {
//						status.setStatus(onlineStatus);
//						status = updateAttributes((CustomArticleBean)articleBean, status);
//
//						if(status != null && status.getStatus() != null && !status.getStatus().getAttributes().isEmpty()){
//
////							if(StringUtils.equals(status.getStatus().getAttributes().get(0), STATUS_ATTR_UNPUBLISHED)){
////
////								//if we have an unpublished attribute, we need to change the web status....
////								WFStatusBean newStatus = new WebStatusUtils(contentManager).getStatusById("unpublished");
////								status.setStatus(newStatus);
////							}
//						}
//
//						//TODO is this needed?
//						final ContentWrite<Object> cw = new ContentWriteBuilder<Object>()
//							.origin(content.getId())
//							.aspects(content.getAspects())
//							.mainAspectData(content.getContentData())
//							.aspect(PublishingBean.ASPECT_NAME, new PublishingBean(
//								escenicId,
//								content.getContentDataType(),
//								"system").action(PublishingBean.PUBLISH_ACTION)).buildUpdate();
//						contentManager.update(content.getId().getContentId(), cw, Subject.NOBODY_CALLER);
//
//					}
//				}
//			}
//		}
//	}
//
//
//	private enum EmbargoState {
//		TIMEOFF_PASSED,
//		TIMEON_PASSED,
//		EMBARGOED,
//		NOEMBARGO
//	}

}
