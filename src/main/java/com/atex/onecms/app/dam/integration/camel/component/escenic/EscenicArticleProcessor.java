package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.engagement.EngagementElement;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToDeserializeContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToExtractLocationException;
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
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class EscenicArticleProcessor extends EscenicContentProcessor {

	private static EscenicArticleProcessor instance;

	public EscenicArticleProcessor() {
	}

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

	protected  String process(Entry existingEntry, OneArticleBean article,
									final ContentResult source, String location, List<EscenicContent> escenicContentList)
		throws CallbackException {

		if (existingEntry != null) {
			EscenicSocialEmbedProcessor.getInstance().updateIdsForEscenicContent(existingEntry, escenicContentList);
		}

		return processArticle(article, source, existingEntry, escenicContentList, escenicConfig);
		//source/data/aspects to get all aspects
	}

	private String processArticle(OneArticleBean article, ContentResult source, Entry existingEntry, List<EscenicContent> escenicContentList, EscenicConfig escenicConfig) {
		Entry entry = new Entry();
		Title title = new Title();
		title.setType("text");
		title.setTitle(escenicUtils.escapeHtml(article.getName()));
		Payload payload = new Payload();
		Content content = new Content();
		Control control = new Control();
		control.setDraft("no");
		control.setState(setArticleState(article, existingEntry));

		String embargoTime = null;
		if (article.getTimeState() != null) {
			long onTime = article.getTimeState().getOntime();
			long offTime = article.getTimeState().getOfftime();
			if (onTime > 0) {
				//check if it's in the past?
				//possibly need to structure it
//				<vdf:value>2020-05-17T15:36:00Z</vdf:value>
				Date d = new Date(onTime);
				embargoTime = d.toInstant().truncatedTo(ChronoUnit.SECONDS).toString();
				if (StringUtils.isNotBlank(embargoTime)) {
					entry.setAvailable(embargoTime);
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

		List<Field> fields = generateArticleFields(article, source, escenicContentList, existingEntry, embargoTime);
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
			entry = processExistingArticle(existingEntry, entry, escenicContentList);
		}

		return escenicUtils.serializeXml(entry);
	}

	private List<State> setArticleState(OneArticleBean article, Entry existingEntry) {
		State state = new State();
		State embargoState = new State();
		state.setState("published");
		state.setName("published");

		if (article.getTimeState() != null) {
			if (article.getTimeState().getOntime() > 0) {

				//todo add logic to remove it if offtime set?
				//ontime present
				embargoState.setName("pre-active");
			}
		}


		return Arrays.asList(state, embargoState);
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

	protected List<Field> generateArticleFields(OneArticleBean oneArticleBean, ContentResult source, List<EscenicContent> escenicContentList, Entry existingEntry, String embargoTime) {

		List<Field> fields = new ArrayList<Field>();

		CustomArticleBean articleBean = (CustomArticleBean) oneArticleBean;
		fields.add(escenicUtils.createField("title", (articleBean.getName()), null, null));
		fields.add(escenicUtils.createField("headlinePrefix", articleBean.getHeadlinePrefix(), null, null));
		fields.add(escenicUtils.createField("articleFlagLabel", "none", null, null));
		fields.add(escenicUtils.createField("articleLayout", articleBean.getArticleType().toLowerCase(), null, null));
		fields.add(escenicUtils.createField("headline", escenicUtils.getStructuredText(articleBean.getSubHeadline()), null, null)); //TODO Check what needs to go in there..
		fields.add(escenicUtils.createField("byline", articleBean.getByline(), null, null));
		fields.add(escenicUtils.createField("originalSource", articleBean.getSource(), null, null));
		fields.add(escenicUtils.createField("leadtext", escenicUtils.getStructuredText(articleBean.getLead()), null, null));
		fields.add(escenicUtils.createField("body", escenicUtils.convertStructuredTextToEscenic(escenicUtils.getStructuredText(articleBean.getBody()), escenicContentList), null, null));
		fields.add(escenicUtils.createField("summaryIcon", "automatic", null, null));
		fields.add(escenicUtils.createField("appearInLatestNews", "true", null, null));
		fields.add(escenicUtils.createField("appearInNLAFeed", "true", null, null));
		fields.add(escenicUtils.createField("premium", String.valueOf(articleBean.isPremiumContent()), null, null));

		if (StringUtils.isNotBlank(embargoTime)) {
			fields.add(escenicUtils.createField("displayDate", embargoTime, null, null));
		}

		return fields;
	}


	protected EscenicContent processTopElement(ContentResult<Object> cr, ContentManager contentManager, DamEngagementUtils utils,
											   PolicyCMServer cmServer, OneArticleBean article, Entry existingEntry,
											   List<EscenicContent> escenicContentList, String sectionId) throws EscenicException {

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
							escenicImage.setOnecmsContentId(contentId);
							escenicImage.setTopElement(true);
							escenicImage.setInlineElement(false);

							String existingEscenicLocation = null;
							try {
								existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
							} catch (CMException e) {
								e.printStackTrace();
							}

							boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);

							Entry escenicImageEntry = null;
							String existingEscenicId = null;
							if (isUpdate) {
								//load image + merge it with existing image and send an update? + process the response?
								existingEscenicId = extractIdFromLocation(existingEscenicLocation);
								if (StringUtils.isNotEmpty(existingEscenicId)) {

									try {
										escenicImageEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation);
									} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
										e.printStackTrace();
									}
								}
							}
							CloseableHttpResponse response = null;
							response = EscenicImageProcessor.getInstance().processImage(contentCr, escenicImageEntry, existingEscenicLocation, cmServer, escenicConfig, escenicImage, sectionId);

							EngagementDesc engagementDesc = evaluateResponse(contentId, existingEscenicLocation, existingEscenicId, false, response, utils, contentCr);

							String escenicLocation = null;
							String escenicId = null;

							if (engagementDesc != null) {

								if (StringUtils.isNotBlank(engagementDesc.getAppPk())) {
									escenicId = engagementDesc.getAppPk();
								}

								if (engagementDesc.getAttributes() != null) {
									for (EngagementElement element : engagementDesc.getAttributes()) {
										if (element != null) {
											if (StringUtils.equalsIgnoreCase(element.getName(), "location")) {
												escenicLocation = element.getValue();
											}
										}
									}
								}

							} else {
								escenicLocation = existingEscenicLocation;
								escenicId = existingEscenicId;

							}

							escenicImage.setEscenicId(escenicId);
							escenicImage.setEscenicLocation(escenicLocation);
							//TODO hack to change the URL from what's provided to a thumbnail url --> alternative is to query for content and read it.
							escenicImage.setThumbnailUrl(escenicLocation.replaceAll("escenic/content", "thumbnail/article"));
							List<Link> links = escenicUtils.generateLinks(escenicImage);
							escenicImage.setLinks(links);


							return escenicImage;

						}


					} else if (contentData instanceof DamCollectionAspectBean) {
						DamCollectionAspectBean collectionAspectBean = (DamCollectionAspectBean) contentData;
						if (collectionAspectBean != null) {
							String existingEscenicLocation = null;
							try {
								existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
							} catch (CMException e) {
								//TODO
								e.printStackTrace();
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
								existingEscenicId = extractIdFromLocation(existingEscenicLocation);
								if (StringUtils.isNotEmpty(existingEscenicId)) {
									try {
										existingGalleryEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation);
									} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
										e.printStackTrace();
									}
								}
							} else {
								escenicGallery.setOnecmsContentId(contentId);
								List<ContentId> collectionItems = collectionAspectBean.getContents();

								for (ContentId id : collectionItems) {

									ContentResult<Object> item = escenicUtils.checkAndExtractContentResult(id, contentManager);
									if (item != null) {
										Object bean = escenicUtils.extractContentBean(item);

										if (bean != null && bean instanceof OneImageBean) {
											EscenicImage escenicImage = new EscenicImage();
											escenicImage.setOnecmsContentId(id);
											escenicImage = EscenicImageProcessor.getInstance().processImage(id, escenicImage, utils, collectionEscenicItems, sectionId);

											if (escenicImage != null) {
												collectionEscenicItems.add(escenicImage);
											} else {
												log.error("Something went wrong while processing an image with id: " + IdUtil.toIdString(id));
											}

										}
									}
								}
							}

							CloseableHttpResponse response = EscenicGalleryProcessor.getInstance().processGallery(collectionAspectBean, existingGalleryEntry, existingEscenicLocation, escenicGallery, escenicConfig, sectionId);


							int statusCode = response.getStatusLine().getStatusCode();
							if (statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NO_CONTENT) {
								String escenicLocation = null;
								try {
									escenicLocation = retrieveEscenicLocation(response);
								} catch (IOException | FailedToExtractLocationException e) {
									e.printStackTrace();
								}
								String escenicId = extractIdFromLocation(escenicLocation);
								final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
								escenicGallery.setEscenicId(escenicId);
								escenicGallery.setEscenicLocation(escenicLocation);
								//TODO don't like this rly...
								escenicGallery.setThumbnailUrl(escenicLocation.replaceAll("escenic/content", "thumbnail/article"));
								escenicGallery.setTitle(collectionAspectBean.getHeadline());
								List<Link> links = escenicUtils.generateLinks(escenicGallery);
								escenicGallery.setLinks(links);
								//atempt to update the engagement
								processEngagement(contentId, engagement, existingEscenicLocation, utils, cr);

								return escenicGallery;
							} else {
								log.error("The server returned : " + response.getStatusLine() + " when attempting to sendimage id: " + contentId);
							}
						}

					}
				}
			} else {
				//content result blank or failed
				log.error("Failed to retrieve content result");
			}
		}

		return null;
	}


}
