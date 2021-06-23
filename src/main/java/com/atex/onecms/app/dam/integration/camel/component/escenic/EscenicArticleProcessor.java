package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicResponseException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToDeserializeContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToRetrieveEscenicContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.*;
import com.atex.onecms.app.dam.standard.aspects.*;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.callback.CallbackException;
import com.polopoly.cm.client.CMException;
import com.polopoly.siteengine.structure.SitePolicy;
import net.sf.saxon.om.One;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EscenicArticleProcessor extends EscenicContentProcessor {

	private static EscenicArticleProcessor instance;
	protected static final Logger LOGGER = Logger.getLogger(EscenicArticleProcessor.class.getName());

	public EscenicArticleProcessor(EscenicUtils escenicUtils) {
		super(escenicUtils);
	}

	public synchronized static EscenicArticleProcessor getInstance() {
		if (instance == null) {
			throw new RuntimeException("EscenicArticleProcessor not initialized");
		}
		return instance;
	}

	public synchronized static void initInstance(EscenicUtils escenicUtils) {
		if (instance == null) {
			instance = new EscenicArticleProcessor(escenicUtils);
		}
	}

    protected String process(Entry existingEntry,
                             OneArticleBean article,
                             List<EscenicContent> escenicContentList,
                             String action,
                             Websection websection, ContentId contentId, ContentResult contentResult) throws CallbackException, EscenicException, CMException {

		if (existingEntry != null) {
			EscenicSocialEmbedProcessor.getInstance().updateIdsForEscenicContent(existingEntry, escenicContentList);
		}

		return processArticle(article, existingEntry, escenicContentList, websection, action, contentId, contentResult);
	}

    private String processArticle(OneArticleBean article,
                                  Entry existingEntry,
                                  List<EscenicContent> escenicContentList, Websection websection,
                                  String action, ContentId contentId, ContentResult contentResult) throws EscenicException, CMException {

		Entry entry = new Entry();
		Title title = escenicUtils.createTitle(escenicUtils.processStructuredTextField(article, "headline"), "text");
		Summary summary = escenicUtils.createSummary(escenicUtils.processStructuredTextField(article, "subHeadline"), "text");
		entry.setTitle(title);
		entry.setSummary(summary);
		Payload payload = new Payload();
		Content content = new Content();
		Control control = new Control();
		Publication publication = generatePublication(article, websection, contentResult);
		entry.setPublication(publication);

		if (StringUtils.equalsIgnoreCase(action, EscenicProcessor.UNPUBLISH_ACTION)) {
			control.setDraft("yes");
			control.setState(setArticleState(article, existingEntry, UNPUBLISHED_STATE));
		} else {
			control.setDraft("no");
			control.setState(setArticleState(article, existingEntry, PUBLISHED_STATE));
		}

		String embargoTime = null;
		if (article.getTimeState() != null) {
			long onTime = article.getTimeState().getOntime();
			long offTime = article.getTimeState().getOfftime();
			if (onTime > 0) {
				//TODO check if it's in the past?
				Date d = new Date(onTime);
				embargoTime = d.toInstant().truncatedTo(ChronoUnit.SECONDS).toString();
				if (StringUtils.isNotBlank(embargoTime)) {
					entry.setAvailable(embargoTime);
					entry.setPublished(embargoTime);
				}
			}

			if (offTime > 0) {
				Date d = new Date(offTime);

				String offTimeString = d.toInstant().truncatedTo(ChronoUnit.SECONDS).toString();
				if (StringUtils.isNotBlank(offTimeString)) {
					entry.setExpires(offTimeString);
				}

			}
		}

		List<Field> fields = generateArticleFields(article, escenicContentList, contentId);
		payload.setField(fields);
		payload.setModel(escenicUtils.getEscenicModel(websection, EscenicArticle.ARTICLE_MODEL_TYPE));
		content.setPayload(payload);
		content.setType(Content.TYPE);
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
			LOGGER.info("entry is" + entry);
			entry = escenicUtils.processExitingContent(existingEntry, entry, true);
		}

		return escenicUtils.serializeXml(entry);
	}

	private Publication generatePublication(OneArticleBean article, Websection websection, ContentResult contentResult) throws CMException, EscenicException {
		Publication publication = new Publication();
		publication.setTitle(websection.getPublicationName());
		publication.setHref(escenicConfig.getModelUrl() + websection.getPublicationName());
		List<Link> sectionLinks = new ArrayList<>();

		Websection homeSection = extractSectionId(contentResult);
		sectionLinks.add(generateHomeSection(homeSection.getEscenicId(), homeSection.getPublicationName()));


		InsertionInfoAspectBean insertionInfoAspectBean = (InsertionInfoAspectBean) contentResult.getContent().getAspectData(InsertionInfoAspectBean.ASPECT_NAME);
		List<ContentId> associatedSitesIds = insertionInfoAspectBean.getAssociatedSites();
		if (associatedSitesIds != null && !associatedSitesIds.isEmpty()) {
			for (ContentId associatedSitesId : associatedSitesIds) {
				SitePolicy associatedSitePolicy = (SitePolicy) cmServer.getPolicy(IdUtil.toPolicyContentId(associatedSitesId));
				if (associatedSitePolicy != null) {
					Websection associatedSection = buildWebsection(associatedSitePolicy, associatedSitesId, true);
					if(associatedSection != null) {
						sectionLinks.add(generateSection(associatedSection.getEscenicId(), associatedSection.getPublicationName()));
					}
				}
			}
		}

		publication.setLink(sectionLinks);
		return publication;
	}



	private Link generateSection(String escenicId, String publicationName) {
		Link link = new Link();
		link.setHref(escenicConfig.getApiUrl() + "/webservice/escenic/section/" + escenicId);
		link.setType("application/atom+xml; type=entry");
		link.setTitle(publicationName);
		link.setRel("http://www.vizrt.com/types/relation/section");
		return link;
	}

	private Link generateHomeSection(String escenicId, String publicationName){
		Link link = new Link();
		link.setRel("http://www.vizrt.com/types/relation/home-section");
		link.setType("application/atom+xml; type=entry");
		link.setHref(escenicConfig.getApiUrl() + "/webservice/escenic/section/" + escenicId);
		link.setTitle(publicationName);
		return link;
	}

	private List<State> setArticleState(OneArticleBean article, Entry existingEntry, String stateText) {
		State state = new State();
		State embargoState = new State();
		state.setState(stateText);
		state.setName(stateText);

		if (article.getTimeState() != null && StringUtils.equalsIgnoreCase("published", stateText)) {
			if (article.getTimeState().getOntime() > 0) {
				embargoState.setName(PREACTIVE_STATE);
			}
		}
		return Arrays.asList(state, embargoState);
	}

	protected List<Field> generateArticleFields(OneArticleBean oneArticleBean, List<EscenicContent> escenicContentList, ContentId contentId) throws EscenicException {

		List<Field> fields = new ArrayList();

		fields.add(escenicUtils.createField("title", escenicUtils.escapeXml(escenicUtils.processStructuredTextField(oneArticleBean, "headline")), null, null));
		fields.add(escenicUtils.createField("headlinePrefix", escenicUtils.getField(oneArticleBean, "headlinePrefix"), null, null));
		fields.add(escenicUtils.createField("articleFlagLabel", escenicUtils.getFieldValueFromPropertyBag(oneArticleBean, "headlineLabel"), null, null));
		fields.add(escenicUtils.createField("articleLayout", escenicUtils.getField(oneArticleBean, "articleType").toLowerCase(), null, null));
		fields.add(escenicUtils.createField("byline", oneArticleBean.getByline(), null, null));
		fields.add(escenicUtils.createField("originalSource", oneArticleBean.getSource(), null, null));
		fields.add(escenicUtils.createField("leadtext", escenicUtils.getFirstBodyParagraph(escenicUtils.getStructuredText(oneArticleBean.getBody())), null, null));
		fields.add(escenicUtils.createField("body", escenicUtils.convertStructuredTextToEscenic(escenicUtils.removeFirstParagraph(escenicUtils.getStructuredText(oneArticleBean.getBody())), escenicContentList), null, null));
		fields.add(escenicUtils.createField("summary", escenicUtils.convertStructuredTextToEscenic(escenicUtils.getStructuredText(oneArticleBean, "subHeadline"), null), null, null));
		fields.add(escenicUtils.createField("subscriptionProtected", escenicUtils.getFieldValueFromPropertyBag(oneArticleBean, "premiumContent"), null, null));
		fields.add(escenicUtils.createField("allowCUEUpdates", "false", null, null));
		fields.add(escenicUtils.createField("com.escenic.tags", null, null, EscenicTagProcessor.getInstance().process(contentId)));
		return fields;
	}

	protected List<EscenicContent> processTopElements(OneArticleBean article, Websection websection, String action) throws EscenicException {

		List<EscenicContent> topElements = new ArrayList<>();
		if (escenicUtils.resourcesPresent(article)) {
			for (ContentId resourceContentId : article.getResources()) {
				final EscenicContent escenicContent = processElement(resourceContentId, websection, action);
				if (escenicContent != null) {
					topElements.add(escenicContent);
				}
			}
		}

		if (escenicUtils.imagesPresent(article)) {
			for (ContentId imagesContentId : article.getImages()) {
				final EscenicContent escenicContent = processElement(imagesContentId, websection, action);
				if (escenicContent != null) {
					topElements.add(escenicContent);
				}
			}
		}
		return topElements;
	}

	protected EscenicContent processElement(ContentId contentId, Websection websection, String action) throws EscenicException {

		ContentResult<OneContentBean> contentCr = escenicUtils.checkAndExtractContentResult(contentId, contentManager);

		if (contentCr != null && contentCr.getStatus().isSuccess()) {
			Object contentData = escenicUtils.extractContentBean(contentCr);
			if (contentData != null) {
				if (contentData instanceof OneImageBean) {
					OneImageBean oneImageBean = (OneImageBean) contentData;

					if (!oneImageBean.isNoUseWeb()) {
						EscenicImage escenicImage = new EscenicImage();
						escenicImage.setTopElement(true);
						escenicImage.setInlineElement(false);

						String existingEscenicLocation = null;
						try {
							existingEscenicLocation = getEscenicIdFromEngagement(contentId);
						} catch (CMException e) {
							throw new EscenicException("Failed to process id from the engagement for id:  " + IdUtil.toIdString(contentId), e);
						}

						boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);

						Entry escenicImageEntry = null;
						String existingEscenicId = null;
						if (isUpdate) {
							//load image description from escenic
							//merge it with updated image description from desk
							//send an update & process the response
							existingEscenicId = escenicUtils.extractIdFromLocation(existingEscenicLocation);
							if (StringUtils.isNotEmpty(existingEscenicId)) {

								try {
									escenicImageEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation);
								} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
									throw new EscenicException("Failed generate escenic image entry for image: " + IdUtil.toIdString(contentId), e);
								}
							}
						}

                        try (CloseableHttpResponse response =
                                 EscenicImageProcessor.getInstance().processImage(contentCr,
                                                                                  escenicImageEntry,
                                                                                  existingEscenicLocation,
                                                                                  escenicImage,
                                                                                  websection)) {

							EngagementDesc engagementDesc = evaluateResponse(contentId, existingEscenicLocation, existingEscenicId, true, response, contentCr, action);
							String escenicId = escenicUtils.getEscenicIdFromEngagement(engagementDesc, existingEscenicId);
							String escenicLocation = escenicUtils.getEscenicLocationFromEngagement(engagementDesc, existingEscenicLocation);
							EscenicImageProcessor.getInstance().assignProperties(oneImageBean, escenicImage, escenicId, escenicLocation, contentId, websection);
							return escenicImage;

						} catch (EscenicResponseException | IOException e) {
                            throw new EscenicException("Error occurred while processing an image: " + IdUtil.toIdString(contentId) , e);
						}
					} else {
						LOGGER.finest("Image with id: " + IdUtil.toIdString(contentId) + " was marked not for web.");
					}
				} else if (contentData instanceof DamCollectionAspectBean) {
					DamCollectionAspectBean collectionAspectBean = (DamCollectionAspectBean) contentData;
					if (collectionAspectBean != null) {
						String existingEscenicLocation = null;
						try {
							existingEscenicLocation = getEscenicIdFromEngagement(contentId);
						} catch (CMException e) {
							throw new EscenicException("Failed to retrieve existing escenic location from engagement for id: " + IdUtil.toIdString(contentId), e);
						}
						boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);
						EscenicGallery escenicGallery = new EscenicGallery();
						escenicGallery.setOnecmsContentId(contentId);
						escenicGallery.setTopElement(true);
						escenicGallery.setInlineElement(false);
						List<EscenicContent> collectionEscenicItems = escenicGallery.getContentList();
						String existingEscenicId = null;
						Entry existingGalleryEntry = null;
						if (isUpdate) {
							existingEscenicId = escenicUtils.extractIdFromLocation(existingEscenicLocation);
							if (StringUtils.isNotEmpty(existingEscenicId)) {
								try {
									existingGalleryEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation);
								} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
                                    throw new EscenicException("Failed to generate existing escenic entry for id: " + IdUtil.toIdString(contentId), e);
								}
							}
						}
						List<ContentId> collectionItems = collectionAspectBean.getContents();

						if (collectionItems != null) {
							for (ContentId id : collectionItems) {

								ContentResult<Object> item = escenicUtils.checkAndExtractContentResult(id, contentManager);
								if (item != null) {
									Object bean = escenicUtils.extractContentBean(item);

									if (bean instanceof OneImageBean) {
										OneImageBean oneImageBean = (OneImageBean) bean;
										if (!oneImageBean.isNoUseWeb()) {
											EscenicImage escenicImage = new EscenicImage();
											escenicImage = EscenicImageProcessor.getInstance().processImage(id, escenicImage, websection, action);

											if (escenicImage != null) {
												collectionEscenicItems.add(escenicImage);
											} else {
                                                throw new EscenicException("Something went wrong while processing an image with id: " + IdUtil.toIdString(contentId));
											}
										}
										LOGGER.finest("Image with id: " + IdUtil.toIdString(id) + " was marked not for web.");
									}
								}
							}
						}

                        try (CloseableHttpResponse response =
                                 EscenicGalleryProcessor.getInstance().processGallery(collectionAspectBean,
                                                                                      existingGalleryEntry,
                                                                                      existingEscenicLocation,
                                                                                      escenicGallery,
                                                                                      websection)) {
                            EngagementDesc engagementDesc = evaluateResponse(contentId, existingEscenicLocation, existingEscenicId, true, response, contentCr, action);

                            String escenicId = escenicUtils.getEscenicIdFromEngagement(engagementDesc, existingEscenicId);
                            String escenicLocation = escenicUtils.getEscenicLocationFromEngagement(engagementDesc, existingEscenicLocation);
                            EscenicGalleryProcessor.getInstance().assignProperties(collectionAspectBean, escenicGallery, escenicId, escenicLocation, contentId, websection);
                            return escenicGallery;
                        } catch (IOException e) {
                            throw new EscenicException("Error occurred while processing a gallery: " + IdUtil.toIdString(contentId), e);
                        }
                    }
				} else if (contentData instanceof ExternalReferenceVideoBean) {
					ExternalReferenceVideoBean externalReferenceVideoBean = (ExternalReferenceVideoBean) contentData;
					EscenicContentReference escenicContentReference = new EscenicContentReference();
					escenicContentReference.setTopElement(true);
					escenicContentReference.setInlineElement(false);
					if (externalReferenceVideoBean != null) {
						EscenicRelatedContentProcessor.getInstance().assignEscenicContentProperties(externalReferenceVideoBean, contentId, escenicContentReference, websection);
					}
					return escenicContentReference;
				}
			}
		} else {
			//content result blank or failed
            throw new EscenicException("Failed to retrieve content result " + IdUtil.toIdString(contentId) + ". Unable to proceed");

		}

		return null;
	}

}