package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.*;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.*;
import com.atex.onecms.app.dam.standard.aspects.DamCollectionAspectBean;
import com.atex.onecms.app.dam.standard.aspects.OneImageBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.IdUtil;
import com.polopoly.cm.client.CMException;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class EscenicGalleryProcessor extends EscenicSmartEmbedProcessor {

	private static EscenicGalleryProcessor instance;
	private static final Logger LOGGER = Logger.getLogger(EscenicGalleryProcessor.class.getName());


	public EscenicGalleryProcessor(EscenicUtils escenicUtils) {
		super(escenicUtils);
	}

	public static EscenicGalleryProcessor getInstance() {
		if (instance == null) {
			throw new RuntimeException("EscenicGalleryProcessor not initialized");
		}
		return instance;
	}

	public static void initInstance(EscenicUtils escenicUtils) {
		if (instance == null) {
			instance = new EscenicGalleryProcessor(escenicUtils);
		}
	}

	protected EscenicGallery processGallery(ContentId contentId, Websection websection, String action) throws EscenicException {

		EscenicGallery escenicGallery = new EscenicGallery();
		List<EscenicContent> collectionEscenicItems = escenicGallery.getContentList();

		ContentResult collectionCr = escenicUtils.checkAndExtractContentResult(contentId, contentManager);

		if (collectionCr != null && collectionCr.getStatus().isSuccess()) {
			Object contentBean = escenicUtils.extractContentBean(collectionCr);
			if (contentBean instanceof DamCollectionAspectBean) {

				DamCollectionAspectBean collectionAspectBean = (DamCollectionAspectBean) contentBean;

				if (collectionAspectBean != null) {

					String existingEscenicLocation = null;
					try {
						existingEscenicLocation = getEscenicIdFromEngagement(contentId);
					} catch (CMException e) {
						throw new FailedToExtractLocationException("Failed to extract escenic location for content id: " + contentId);
					}
					boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);

					String existingEscenicId = null;
					Entry existingGalleryEntry = null;
					if (isUpdate) {
						existingEscenicId = escenicUtils.extractIdFromLocation(existingEscenicLocation);
						if (StringUtils.isNotEmpty(existingEscenicId)) {

							try {
								existingGalleryEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation);
							} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
								throw new RuntimeException("Failed to generate existing gallery entry");
							}
						}
					} else {
						escenicGallery.setOnecmsContentId(contentId);
					}

						List<ContentId> collectionItems = collectionAspectBean.getContents();

						for (ContentId id : collectionItems) {

							ContentResult item = escenicUtils.checkAndExtractContentResult(id, contentManager);

							if (item != null && item.getStatus().isSuccess()) {
								Object bean = escenicUtils.extractContentBean(item);

								if (bean instanceof OneImageBean) {
									if (!((OneImageBean) bean).isNoUseWeb()) {
										EscenicImage escenicImage = new EscenicImage();
										escenicImage = EscenicImageProcessor.getInstance().processImage(id, escenicImage, websection, action);
										if (escenicImage != null) {
											collectionEscenicItems.add(escenicImage);
										} else {
											throw new RuntimeException("Something went wrong while procesing an image with id: " + IdUtil.toIdString(id));
										}
									} else {
										LOGGER.finest("Image with id: " + IdUtil.toIdString(id) + " was marked not for web.");
									}
								}
							}
						}

                    try (CloseableHttpResponse response = processGallery(collectionAspectBean,
                                                                         existingGalleryEntry,
                                                                         existingEscenicLocation,
                                                                         escenicGallery,
                                                                         websection)) {

						EngagementDesc engagementDesc = evaluateResponse(contentId, existingEscenicLocation, existingEscenicId, true, response, collectionCr, action);
						String escenicId = escenicUtils.getEscenicIdFromEngagement(engagementDesc, existingEscenicId);
						String escenicLocation = escenicUtils.getEscenicLocationFromEngagement(engagementDesc, existingEscenicLocation);
						assignProperties(collectionAspectBean, escenicGallery, escenicId, escenicLocation, contentId, websection);

					} catch (IOException e) {
						throw new EscenicResponseException("Failed to process a response for gallery: " + IdUtil.toIdString(collectionCr.getContentId().getContentId()));
					}
				}

			}
		}
		return escenicGallery;
	}

    protected void assignProperties(DamCollectionAspectBean collectionAspectBean,
                                    EscenicGallery escenicGallery,
                                    String escenicId,
                                    String escenicLocation,
                                    ContentId contentId,
                                    Websection websection) {

		escenicGallery.setEscenicId(escenicId);
		escenicGallery.setEscenicLocation(escenicLocation);
		escenicGallery.setOnecmsContentId(contentId);
		escenicGallery.setThumbnailUrl(escenicLocation.replaceAll("escenic/content", "thumbnail/article"));
		escenicGallery.setTitle(escenicUtils.escapeXml(collectionAspectBean.getHeadline()));
		List<Link> links = escenicUtils.generateLinks(escenicGallery, websection);
		escenicGallery.setLinks(links);
	}

    public CloseableHttpResponse processGallery(DamCollectionAspectBean collectionAspectBean,
                                                Entry existingGalleryEntry,
                                                String existingEscenicLocation,
                                                EscenicGallery escenicGallery,
                                                Websection websection) throws FailedToSendContentToEscenicException {

		CloseableHttpResponse response = null;
		if (escenicGallery != null && collectionAspectBean != null) {
			Entry entry = constructAtomEntryForGallery(websection, collectionAspectBean, escenicGallery);

			if (existingGalleryEntry != null) {
				entry = escenicUtils.processExitingContent(existingGalleryEntry, entry, false);
			}

			String xml = escenicUtils.serializeXml(entry);

			if (StringUtils.isNotEmpty(xml) && existingGalleryEntry != null) {
				response = escenicUtils.sendUpdatedContentToEscenic(existingEscenicLocation, xml);
			} else {
				response = escenicUtils.sendNewContentToEscenic(xml, websection);
			}
		}
		return response;
	}

	private Entry constructAtomEntryForGallery(Websection websection, DamCollectionAspectBean collectionAspectBean, EscenicGallery escenicGallery) {
		if (collectionAspectBean != null && escenicGallery != null) {
			Entry entry = new Entry();
			Title title = escenicUtils.createTitle(collectionAspectBean.getHeadline(), "text");
			entry.setTitle(title);

			Payload payload = new Payload();
			Content content = new Content();

			Control control = escenicUtils.generateControl("no", PUBLISHED_STATE);
			List<Field> fields = generateGalleryFields(collectionAspectBean, escenicGallery);
			payload.setField(fields);

			List<Link> links = generateLinksForGallery(escenicGallery, websection);
			entry.setLink(links);
			payload.setModel(escenicUtils.getEscenicModel(websection,EscenicGallery.GALLERY_MODEL_CONTENT_TYPE));
			content.setPayload(payload);
			content.setType(Content.TYPE);
			entry.setContent(content);
			entry.setControl(control);

			return entry;
		} else {
			throw new RuntimeException("Failed to create atom entry for a gallery");
		}
	}

	protected List<Field> generateGalleryFields(DamCollectionAspectBean collectionAspectBean, EscenicGallery escenicGallery) {
		List<Field> fields = new ArrayList<>();
		fields.add(escenicUtils.createField("title", collectionAspectBean.getHeadline(), null, null));
		fields.add(escenicUtils.createField("leadtext", collectionAspectBean.getDescription(), null, null));
		return fields;
	}

	private List<Link> generateLinksForGallery(EscenicGallery escenicGallery, Websection websection) {
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

						List<Field> fields = new ArrayList<>();
						fields.add(escenicUtils.createField("title", escenicImage.getTitle(), null, null));
						fields.add(escenicUtils.createField("caption", escenicImage.getTitle(), null, null));
						Link pictureRelLink = escenicUtils.createLink(fields, EscenicUtils.PICTURE_RELATIONS_GROUP, escenicImage.getThumbnailUrl(), EscenicImage.IMAGE_MODEL_CONTENT_SUMMARY,
							escenicImage.getEscenicLocation(), EscenicUtils.ATOM_APP_ENTRY_TYPE, EscenicUtils.RELATED, escenicImage.getEscenicId(),
							escenicImage.getTitle(), PUBLISHED_STATE, websection);
						links.add(pictureRelLink);
					}
				}
			}
		}
		return links;
	}

}
