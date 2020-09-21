package com.atex.onecms.app.dam.integration.camel.component.escenic;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToSendContentToEscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Control;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Entry;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Field;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Payload;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Title;
import com.atex.onecms.app.dam.standard.aspects.OneArticleBean;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.IdUtil;
import com.polopoly.cm.policy.PolicyCMServer;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;

public class EscenicSmartEmbedProcessor extends EscenicContentProcessor {

	private static EscenicSmartEmbedProcessor instance;
	private static final Logger LOGGER = Logger.getLogger(EscenicSmartEmbedProcessor.class.getName());

	public EscenicSmartEmbedProcessor() {
	}

	public EscenicSmartEmbedProcessor(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
		super(contentManager, cmServer, escenicUtils, escenicConfig);
	}

	public synchronized static EscenicSmartEmbedProcessor getInstance() {
		if (instance == null) {
			throw new RuntimeException("EscenicContentProcessor not initialized");
		}
		return instance;
	}

	public static synchronized void initInstance(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
		if (instance == null) {
			instance = new EscenicSmartEmbedProcessor(contentManager, cmServer, escenicUtils, escenicConfig);
		}
	}

	protected List<EscenicContent> process(ContentResult<Object> cr, DamEngagementUtils utils, OneArticleBean article, Entry entry, String sectionId, String action) throws IOException, URISyntaxException, EscenicException {
		LOGGER.finest("Processing smart embeds");
		List<EscenicContent> escenicContentList = new ArrayList<>();
		if (cr.getStatus().isSuccess()) {
			List<CustomEmbedParser.SmartEmbed> embeds = EscenicSocialEmbedProcessor.getInstance().processEmbeds(article.getBody().getText());
			Content c = cr.getContent();
			if (c != null) {

				for (CustomEmbedParser.SmartEmbed embed : embeds) {
					if (embed != null) {

						if (StringUtils.isNotEmpty(embed.getObjType())) {
							switch (embed.getObjType()) {
								case EscenicImage.IMAGE_TYPE:
									if (!escenicUtils.isAlreadyProcessed(escenicContentList, embed)) {
										if (embed.getContentId() != null) {
											EscenicImage escenicImage = new EscenicImage();
											escenicImage = EscenicImageProcessor.getInstance().processImage(embed.getContentId(), escenicImage, utils, escenicContentList, sectionId, action);

											if (escenicImage != null) {
												escenicContentList.add(escenicImage);
											} else {
												LOGGER.severe("Something went wrong while processing an image with id: " + IdUtil.toIdString(embed.getContentId()));
											}
										} else {
											LOGGER.severe("Unable to process an inline image as the onecms id was not found in embeded text");
										}
									}

									break;

								case EscenicGallery.GALLERY_TYPE:
									if (!escenicUtils.isAlreadyProcessed(escenicContentList, embed)) {
										if (embed.getContentId() != null) {
											EscenicGallery escenicGallery = EscenicGalleryProcessor.getInstance().processGallery(embed.getContentId(), embed, utils, sectionId, action);
											escenicContentList.add(escenicGallery);
										}
									}

									break;

								case EscenicEmbed.SOCIAL_EMBED_TYPE:

									//special case - if there are duplicates parsed from article body we'll only create one content in escenic
									// and use the ids for all occurrences
									if (!escenicUtils.isAlreadyProcessed(escenicContentList, embed)) {
										EscenicEmbed escenicEmbed = EscenicSocialEmbedProcessor.getInstance().processSocialEmbed(embed, utils, cr.getContent().getId().getContentId(), sectionId, action);
										escenicContentList.add(escenicEmbed);
									}
									break;

								//workaround for smartpase plugin - we're going to pick up all externalReference beans as embeds of data-onecms-type=article type.
								//once we load them individually by onecms id, we can pick up their actual type in escenic for further processing
								case EscenicArticle.ARTICLE_TYPE:
									if (!escenicUtils.isAlreadyProcessed(escenicContentList, embed)) {
										EscenicContentReference escenicContentReference = EscenicRelatedContentProcessor.getInstance().process(embed);
										escenicContentList.add(escenicContentReference);
									}
									break;
							}
						}
					}
				}
			}
		}

		return escenicContentList;
	}

	protected CloseableHttpResponse processEmbed(CustomEmbedParser.SmartEmbed embed,  EscenicConfig escenicConfig, EscenicEmbed escenicEmbed, String sectionId) throws FailedToSendContentToEscenicException {
		String xml = constructAtomEntryForSocialEmbed(escenicEmbed, embed, escenicConfig);
		if (StringUtils.isNotEmpty(xml)) {
			return escenicUtils.sendNewContentToEscenic(xml, sectionId);
		}
		return null;
	}

	private String constructAtomEntryForSocialEmbed(EscenicEmbed escenicEmbed, CustomEmbedParser.SmartEmbed socialEmbed, EscenicConfig escenicConfig) {
		Entry entry = new Entry();
		Title title = escenicUtils.createTitle(socialEmbed.getSocialNetwork(), "text");
		escenicEmbed.setTitle(title.getTitle());
		entry.setTitle(title);
		Payload payload = new Payload();
		com.atex.onecms.app.dam.integration.camel.component.escenic.model.Content content = new com.atex.onecms.app.dam.integration.camel.component.escenic.model.Content();
		Control control = escenicUtils.generateControl("no", PUBLISHED_STATE);
		List<Field> fields = generateSocialEmbedFields(socialEmbed, escenicEmbed);
		payload.setField(fields);
		payload.setModel(escenicConfig.getModelUrl() + EscenicEmbed.EMBED_MODEL_CONTENT_TYPE);
		content.setPayload(payload);
		content.setType(com.atex.onecms.app.dam.integration.camel.component.escenic.model.Content.TYPE);
		entry.setContent(content);
		entry.setControl(control);

		return escenicUtils.serializeXml(entry);
	}

	protected List<Field> generateSocialEmbedFields(CustomEmbedParser.SmartEmbed socialEmbed, EscenicEmbed escenicEmbed) {
		List<Field> fields = new ArrayList<Field>();
		Payload p = new Payload();
		List<Field> embedFields = new ArrayList<Field>();
		List<Field> embedDetailsFields = new ArrayList<Field>();
		embedDetailsFields.add(escenicUtils.createField("network", socialEmbed.getSocialNetwork(), null, null));
		embedDetailsFields.add(escenicUtils.createField("url", socialEmbed.getEmbedUrl(), null, null));
		embedFields.add(escenicUtils.createField("socialEmbeds", null, embedDetailsFields, null));
		embedFields.add(escenicUtils.createField("title", socialEmbed.getSocialNetwork(), null, null));
		p.setField(embedFields);
		com.atex.onecms.app.dam.integration.camel.component.escenic.model.List list = new com.atex.onecms.app.dam.integration.camel.component.escenic.model.List();
		list.setPayload(p);
		fields.add(escenicUtils.createField("socialEmbeds", null, null, list));
		fields.add(escenicUtils.createField("title", socialEmbed.getSocialNetwork(), null, null));
		return fields;
	}
}
