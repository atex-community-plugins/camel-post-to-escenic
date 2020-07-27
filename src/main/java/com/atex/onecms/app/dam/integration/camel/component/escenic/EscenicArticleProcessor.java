package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToDeserializeContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToRetrieveEscenicContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.*;
import com.atex.onecms.app.dam.standard.aspects.*;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.callback.CallbackException;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.PolicyCMServer;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

public class EscenicArticleProcessor extends EscenicContentProcessor {

	private static EscenicArticleProcessor instance;
	protected static final Logger LOGGER = Logger.getLogger(EscenicArticleProcessor.class.getName());

	public EscenicArticleProcessor(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
		super(contentManager, cmServer, escenicUtils, escenicConfig);

	}

	public synchronized static EscenicArticleProcessor getInstance() {
		if (instance == null) {
			throw new RuntimeException("EscenicContentProcessor not initialized");
		}
		return instance;
	}

	public synchronized static void initInstance(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
		if (instance == null) {
			instance = new EscenicArticleProcessor(contentManager, cmServer, escenicUtils, escenicConfig);
		}
	}

	protected String process(Entry existingEntry, CustomArticleBean article, List<EscenicContent> escenicContentList, String action)
		throws CallbackException {

		if (existingEntry != null) {
			EscenicSocialEmbedProcessor.getInstance().updateIdsForEscenicContent(existingEntry, escenicContentList);
		}

		return processArticle(article, existingEntry, escenicContentList, escenicConfig, action);
	}

	private String processArticle(CustomArticleBean article, Entry existingEntry, List<EscenicContent> escenicContentList, EscenicConfig escenicConfig, String action) {

		Entry entry = new Entry();
		Title title = escenicUtils.createTitle(escenicUtils.getStructuredText(article.getHeadline()), "text");
		Summary summary = escenicUtils.createSummary(escenicUtils.getStructuredText(article.getSubHeadline()), "text");
		entry.setTitle(title);
		entry.setSummary(summary);
		Payload payload = new Payload();
		Content content = new Content();
		Control control = new Control();

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

		List<Field> fields = generateArticleFields(article, escenicContentList, embargoTime);
		payload.setField(fields);
		payload.setModel(escenicConfig.getModelUrl() + EscenicArticle.ARTICLE_MODEL_TYPE);
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
			entry = processExistingArticle(existingEntry, entry);
		}

		return escenicUtils.serializeXml(entry);
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

	private Entry processExistingArticle(Entry existingEntry, Entry entry) {
		List<Field> existingFields = existingEntry.getContent().getPayload().getField();
		List<Field> newFields = entry.getContent().getPayload().getField();
		for (Field field : existingFields) {
			for (Field newField : newFields) {
				if (StringUtils.equalsIgnoreCase(field.getName(), newField.getName())) {
					field.setValue(newField.getValue());
				}
			}
		}
		existingEntry.setControl(entry.getControl());
		existingEntry.setLink(escenicUtils.mergeLinks(existingEntry.getLink(), entry.getLink()));
		existingEntry.setTitle(entry.getTitle());
		existingEntry.setAvailable(entry.getAvailable());
		existingEntry.setExpires(entry.getExpires());
		existingEntry.setSummary(entry.getSummary());
		existingEntry.setPublication(escenicUtils.cleanUpPublication(existingEntry.getPublication()));
		return existingEntry;
	}

	protected List<Field> generateArticleFields(OneArticleBean oneArticleBean, List<EscenicContent> escenicContentList, String embargoTime) {

		List<Field> fields = new ArrayList<Field>();

		CustomArticleBean articleBean = (CustomArticleBean) oneArticleBean;
		fields.add(escenicUtils.createField("title", escenicUtils.escapeHtml(escenicUtils.getStructuredText(articleBean.getHeadline())), null, null));
		fields.add(escenicUtils.createField("headlinePrefix", articleBean.getHeadlinePrefix(), null, null));
		fields.add(escenicUtils.createField("articleFlagLabel", articleBean.getKeywords(), null, null));
		fields.add(escenicUtils.createField("articleLayout", articleBean.getArticleType().toLowerCase(), null, null));
		fields.add(escenicUtils.createField("byline", articleBean.getByline(), null, null));
		fields.add(escenicUtils.createField("originalSource", articleBean.getSource(), null, null));
		fields.add(escenicUtils.createField("leadtext", escenicUtils.getFirstBodyParagraph(escenicUtils.getStructuredText(articleBean.getBody())), null, null));
		fields.add(escenicUtils.createField("body", escenicUtils.convertStructuredTextToEscenic(escenicUtils.removeFirstParagraph(escenicUtils.getStructuredText(articleBean.getBody())), escenicContentList), null, null));
		fields.add(escenicUtils.createField("summary", escenicUtils.convertStructuredTextToEscenic(escenicUtils.getStructuredText(articleBean.getSubHeadline()), null), null, null));
		fields.add(escenicUtils.createField("subscriptionProtected", articleBean.getSponsorname(), null, null));
		fields.add(escenicUtils.createField("allowCUEUpdates", "false", null, null));

		return fields;
	}


	protected EscenicContent processTopElement(ContentResult<Object> cr, ContentManager contentManager, DamEngagementUtils utils,
											   PolicyCMServer cmServer, OneArticleBean article, Entry existingEntry,
											   List<EscenicContent> escenicContentList, String sectionId, String action) throws EscenicException {

		if (article != null && article.getTopElement() != null) {
			ContentId contentId = article.getTopElement();
			/**
			 * This is to prevent pushing the image twice if it's both in body & set as top element as well
			 */
			if (contentId != null) {
				if (escenicContentList != null) {
					for (EscenicContent escenicContent : escenicContentList) {
						if (escenicContent != null && escenicContent.getOnecmsContentId() != null) {
							if (StringUtils.equalsIgnoreCase(IdUtil.toIdString(escenicContent.getOnecmsContentId()), IdUtil.toIdString(contentId))) {
								//they're the same, therefore update the escenic content with the flag and return and avoid processing
								if (escenicContent instanceof EscenicImage) {
									EscenicImage img = (EscenicImage) escenicContent;

									if (img != null) {
										img.setTopElement(true);
										return null;
									}
								}

								if (escenicContent instanceof EscenicGallery) {
									EscenicGallery gallery = (EscenicGallery) escenicContent;

									if (gallery != null) {
										gallery.setTopElement(true);
										return null;
									}
								}
							}
						}
					}
				}
			}

			ContentResult<OneContentBean> contentCr = escenicUtils.checkAndExtractContentResult(contentId, contentManager);

			if (contentCr != null && contentCr.getStatus().isSuccess()) {
				Object contentData = escenicUtils.extractContentBean(contentCr);
				if (contentData != null) {
					if (contentData instanceof OneImageBean) {
						OneImageBean oneImageBean = (OneImageBean) contentData;

						if (oneImageBean != null) {
							EscenicImage escenicImage = new EscenicImage();
							escenicImage.setTopElement(true);
							escenicImage.setInlineElement(false);

							String existingEscenicLocation = null;
							try {
								existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
							} catch (CMException e) {
								throw new RuntimeException("Failed to process id from engagement : " + contentId + " - " + e);
							}

							boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);

							Entry escenicImageEntry = null;
							String existingEscenicId = null;
							if (isUpdate) {
								//load image + merge it with existing image and send an update? + process the response?
								existingEscenicId = escenicUtils.extractIdFromLocation(existingEscenicLocation);
								if (StringUtils.isNotEmpty(existingEscenicId)) {

									try {
										escenicImageEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation);
									} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
										throw new RuntimeException("Failed generate escenic image entry : " + e);
									}
								}
							}

							CloseableHttpResponse response = EscenicImageProcessor.getInstance().processImage(contentCr, escenicImageEntry, existingEscenicLocation, cmServer, escenicConfig, escenicImage, sectionId);

							EngagementDesc engagementDesc = evaluateResponse(contentId, existingEscenicLocation, existingEscenicId, true, response, utils, contentCr, action);
							String escenicId = escenicUtils.getEscenicIdFromEngagement(engagementDesc, existingEscenicId);
							String escenicLocation = escenicUtils.getEscenicLocationFromEngagement(engagementDesc, existingEscenicLocation);
							EscenicImageProcessor.getInstance().assignProperties(oneImageBean, escenicImage, escenicId, escenicLocation, contentId);
							return escenicImage;
						}
					} else if (contentData instanceof DamCollectionAspectBean) {
						DamCollectionAspectBean collectionAspectBean = (DamCollectionAspectBean) contentData;
						if (collectionAspectBean != null) {
							String existingEscenicLocation = null;
							try {
								existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
							} catch (CMException e) {
								throw new RuntimeException("Failed to retrieve existing escenic location from engagement for id: " + contentId);
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
										e.printStackTrace();
									}
								}
							}
							List<ContentId> collectionItems = collectionAspectBean.getContents();

							if (collectionItems != null) {
								for (ContentId id : collectionItems) {

									ContentResult<Object> item = escenicUtils.checkAndExtractContentResult(id, contentManager);
									if (item != null) {
										Object bean = escenicUtils.extractContentBean(item);

										if (bean != null && bean instanceof OneImageBean) {
											EscenicImage escenicImage = new EscenicImage();
											escenicImage = EscenicImageProcessor.getInstance().processImage(id, escenicImage, utils, collectionEscenicItems, sectionId, action);

											if (escenicImage != null) {
												collectionEscenicItems.add(escenicImage);
											} else {
												LOGGER.severe("Something went wrong while processing an image with id: " + IdUtil.toIdString(id));
											}
										}
									}
								}
							}

							CloseableHttpResponse response = EscenicGalleryProcessor.getInstance().processGallery(collectionAspectBean, existingGalleryEntry, existingEscenicLocation, escenicGallery, escenicConfig, sectionId);
							EngagementDesc engagementDesc = evaluateResponse(contentId, existingEscenicLocation, existingEscenicId, true, response, utils, contentCr, action);

							String escenicId = escenicUtils.getEscenicIdFromEngagement(engagementDesc, existingEscenicId);
							String escenicLocation = escenicUtils.getEscenicLocationFromEngagement(engagementDesc, existingEscenicLocation);
							EscenicGalleryProcessor.getInstance().assignProperties(collectionAspectBean, escenicGallery, escenicId, escenicLocation, contentId);
							return escenicGallery;
						}
					}
				}
			} else {
				//content result blank or failed
				LOGGER.severe("Failed to retrieve content result");
			}
		}
		return null;
	}



}
