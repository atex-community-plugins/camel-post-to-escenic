package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToDeserializeContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToExtractLocationException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToRetrieveEscenicContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToSendContentToEscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.*;
import com.atex.onecms.app.dam.standard.aspects.DamCollectionAspectBean;
import com.atex.onecms.app.dam.standard.aspects.OneImageBean;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.IdUtil;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.PolicyCMServer;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EscenicGalleryProcessor extends EscenicSmartEmbedProcessor {

	private static EscenicGalleryProcessor instance;

	public EscenicGalleryProcessor(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
		super(contentManager, cmServer, escenicUtils, escenicConfig);
	}

	public static EscenicGalleryProcessor getInstance() {
		if (instance == null) {
			throw new RuntimeException("EscenicGalleryProcessor not initialized");
		}
		return instance;
	}

	public static void initInstance(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
		if (instance == null) {
			instance = new EscenicGalleryProcessor(contentManager, cmServer, escenicUtils, escenicConfig);
		}

	}


	protected EscenicGallery processGallery(ContentId contentId, CustomEmbedParser.SmartEmbed embed, DamEngagementUtils utils, String sectionId) throws FailedToSendContentToEscenicException {

		EscenicGallery escenicGallery = new EscenicGallery();
		List<EscenicContent> collectionEscenicItems = escenicGallery.getContentList();

		ContentResult collectionCr = escenicUtils.checkAndExtractContentResult(contentId, contentManager);

		if (collectionCr != null && collectionCr.getStatus().isSuccess()) {
			Object contentBean = escenicUtils.extractContentBean(collectionCr);
			if (contentBean != null && contentBean instanceof DamCollectionAspectBean) {

				DamCollectionAspectBean collectionAspectBean = (DamCollectionAspectBean) contentBean;

				if (collectionAspectBean != null) {

					String existingEscenicLocation = null;
					try {
						existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
					} catch (CMException e) {
						//TODO
						e.printStackTrace();
					}
					boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);

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
					}

						List<ContentId> collectionItems = collectionAspectBean.getContents();

						for (ContentId id : collectionItems) {

							ContentResult item = escenicUtils.checkAndExtractContentResult(id, contentManager);

							if (item != null && item.getStatus().isSuccess()) {
								Object bean = escenicUtils.extractContentBean(item);

								if (bean != null && bean instanceof OneImageBean) {
									EscenicImage escenicImage = new EscenicImage();
									escenicImage.setOnecmsContentId(id);
									escenicImage = EscenicImageProcessor.getInstance().processImage(id, escenicImage, utils, collectionEscenicItems, sectionId);
									if (escenicImage != null) {
										collectionEscenicItems.add(escenicImage);
									} else {
//TODO										throw exception and stop
										log.error("Something went wrong while processing an image with id: " + IdUtil.toIdString(id));
									}
								}
							}
						}

					CloseableHttpResponse response = processGallery(collectionAspectBean, existingGalleryEntry, existingEscenicLocation, escenicGallery, escenicConfig, sectionId);


					//todo can't get the status straight away!!
					int statusCode = response.getStatusLine().getStatusCode();

					String escenicLocation = null;
					String escenicId = null;
					if (statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_OK) {

						try {
							escenicLocation = retrieveEscenicLocation(response);
						} catch (IOException | FailedToExtractLocationException e) {
							e.printStackTrace();
						}
						escenicId = extractIdFromLocation(escenicLocation);

					}  else if (statusCode == HttpStatus.SC_NO_CONTENT) {
						escenicLocation = existingEscenicLocation;
						escenicId = existingEscenicId;

					}

					else {
						log.error("The server returned : " + response.getStatusLine() + " when attempting to sendimage id: " + contentId);
					}


					final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
					escenicGallery.setEscenicId(escenicId);
					escenicGallery.setEscenicLocation(escenicLocation);
					escenicGallery.setThumbnailUrl(escenicLocation.replaceAll("escenic/content", "thumbnail/article"));
					escenicGallery.setTitle(collectionAspectBean.getHeadline());
					List<Link> links = escenicUtils.generateLinks(escenicGallery);
					escenicGallery.setLinks(links);
					//atempt to update the engagement
					processEngagement(contentId, engagement, existingEscenicLocation, utils, collectionCr);

				}

			}
		}
		return escenicGallery;

	}

	public CloseableHttpResponse processGallery(DamCollectionAspectBean collectionAspectBean, Entry existingGalleryEntry, String existingEscenicLocation, EscenicGallery escenicGallery, EscenicConfig escenicConfig, String sectionId) throws FailedToSendContentToEscenicException {

		CloseableHttpResponse response = null;
		if (escenicGallery != null && collectionAspectBean != null) {

			Entry atomEntry = constructAtomEntryForGallery(collectionAspectBean, escenicGallery, escenicConfig);
			if (existingGalleryEntry != null) {
				atomEntry = processExistingGallery(existingGalleryEntry, atomEntry);
			}

			String xml = escenicUtils.serializeXml(atomEntry);

			if (StringUtils.isNotEmpty(xml) && existingGalleryEntry != null) {
				response = escenicUtils.sendUpdatedContentToEscenic(existingEscenicLocation, xml);
			} else {
				response = escenicUtils.sendNewContentToEscenic(xml, sectionId);
			}
		}
		return response;
	}

	private Entry processExistingGallery(Entry existingEntry, Entry entry) {
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
						//todo
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

	/**
	 * Constructing an atom entry for binary file:
	 * curl --include -u jwojcik@atex.com:4l+rUbruQE -X POST -H "Content-Type: application/atom+xml" http://inm-test-editorial.inm.lan:8081/webservice/escenic/section/1/content-items --upload-file imageatom.xml
	 * <p>
	 * 3
	 *
	 * @return
	 */
	private Entry constructAtomEntryForGallery(DamCollectionAspectBean collectionAspectBean, EscenicGallery escenicGallery, EscenicConfig escenicConfig) {
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

			List<Link> links = generateLinksForGallery(escenicGallery, escenicConfig);

			//TODO how do we handle updates? we need to somehow merge existing links with the links we generate here...
			//todo we currently reuse whatever is in escenic - thing to consider is what if we want to remove a relationship? or replace it?
			entry.setLink(links);

			//TODO this has to be determined by the type.. if we are going to use the same method for different binary files
//			payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-type/gallery");
			payload.setModel(escenicConfig.getModelUrl() + EscenicGallery.GALLERY_MODEL_CONTENT_TYPE);

			content.setPayload(payload);
			content.setType(Content.TYPE);

			entry.setContent(content);
			entry.setControl(control);

			return entry;
		} else {
			//todo.
			throw new RuntimeException("Could not read properties of an image ");
		}
	}

	protected List<Field> generateGalleryFields(DamCollectionAspectBean collectionAspectBean, EscenicGallery escenicGallery) {
		List<Field> fields = new ArrayList<>();

		fields.add(escenicUtils.createField("name", collectionAspectBean.getName(), null, null));
		if (StringUtils.isEmpty(collectionAspectBean.getName())) {
			fields.add(escenicUtils.createField("title", "No Name", null, null));
		} else {
			fields.add(escenicUtils.createField("title", collectionAspectBean.getName(), null, null));
		}

		fields.add(escenicUtils.createField("leadtext", collectionAspectBean.getHeadline(), null, null));

		return fields;
	}

	private List<Link> generateLinksForGallery(EscenicGallery escenicGallery, EscenicConfig escenicConfig) {
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
						//TODO replace with createLink
						Link pictureRelLink = new Link();
						Payload payload1 = new Payload();
						List<Field> fields1 = new ArrayList<>();
						fields1.add(escenicUtils.createField("title", escenicImage.getTitle(), null, null));
						fields1.add(escenicUtils.createField("caption", escenicImage.getTitle(), null, null));
						payload1.setField(fields1);
						payload1.setModel(escenicConfig.getModelUrl() + EscenicImage.IMAGE_MODEL_CONTENT_SUMMARY);
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
				}
			}
		}
		return links;

	}

}
