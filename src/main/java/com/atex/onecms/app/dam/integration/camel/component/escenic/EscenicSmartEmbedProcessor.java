package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.*;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.*;
import com.atex.onecms.app.dam.standard.aspects.OneArticleBean;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.content.*;
import com.atex.onecms.content.Content;
import com.polopoly.cm.policy.PolicyCMServer;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

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

	//todo check this!
	protected List<EscenicContent> process(ContentResult<Object> cr,DamEngagementUtils utils, OneArticleBean article, Entry entry, String sectionId, String action) throws IOException, URISyntaxException, EscenicException {
		LOGGER.info("Processing smart embeds");
		List<EscenicContent> escenicContentList = new ArrayList<>();
		if (cr.getStatus().isSuccess()) {
			List<CustomEmbedParser.SmartEmbed> embeds = EscenicSocialEmbedProcessor.getInstance().processEmbeds(article.getBody().getText());
			Content c = cr.getContent();
			if (c != null) {

				List<Link> links = null;
				if (entry != null && entry.getLink() != null) {
					links = entry.getLink();
				}
				//at this stage we assume that content does not exist in escenic.. wrong assumption?
				for (CustomEmbedParser.SmartEmbed embed : embeds) {
					if (embed != null) {

						if (StringUtils.isNotEmpty(embed.getObjType())) {
							switch (embed.getObjType()) {
								case EscenicImage.IMAGE_TYPE:
									if (embed.getContentId() != null) {
										EscenicImage escenicImage = new EscenicImage();
//										escenicImage.setOnecmsContentId(embed.getContentId());
										escenicImage = EscenicImageProcessor.getInstance().processImage(embed.getContentId(), escenicImage, utils, escenicContentList, sectionId, action);

										if (escenicImage != null) {
											escenicContentList.add(escenicImage);
										} else {
											LOGGER.severe("Something went wrong while processing an image with id: " + IdUtil.toIdString(embed.getContentId()));
										}
									} else {
										LOGGER.severe("Unable to process an inline image as the onecms id was not found in embeded text");
									}


									break;

								case EscenicGallery.GALLERY_TYPE:
									if (embed.getContentId() != null) {
										EscenicGallery escenicGallery = EscenicGalleryProcessor.getInstance().processGallery(embed.getContentId(), embed, utils, sectionId, action);
										escenicContentList.add(escenicGallery);
									}

									break;

								case EscenicEmbed.SOCIAL_EMBED_TYPE:
									//special case - we won't be sending any updates to social embeds I guess?
									//So in here, if the entry exists, you can reuse what's already on the entry.
									//of course if the embed escenic id matches what's in the link, otherwise create a new one.
									if (entry == null) {
										EscenicEmbed escenicEmbed = EscenicSocialEmbedProcessor.getInstance().processSocialEmbed(embed, utils, cr.getContent().getId().getContentId(), sectionId, action);
										escenicContentList.add(escenicEmbed);
									} else {
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

//										if (!found) {
											//we're dealing with new embed instead..
											EscenicEmbed escenicEmbed = EscenicSocialEmbedProcessor.getInstance().processSocialEmbed(embed, utils, cr.getContent().getId().getContentId(), sectionId, action);
											escenicContentList.add(escenicEmbed);
//										}
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


	/**
	 * Constructing an atom entry for binary file:
	 * curl --include -u jwojcik@atex.com:4l+rUbruQE -X POST -H "Content-Type: application/atom+xml" http://inm-test-editorial.inm.lan:8081/webservice/escenic/section/1/content-items --upload-file imageatom.xml
	 * <p>
	 * //	 * @param binaryLocation
	 *
	 * @return
	 */
	private String constructAtomEntryForSocialEmbed(EscenicEmbed escenicEmbed, CustomEmbedParser.SmartEmbed socialEmbed, EscenicConfig escenicConfig) {
		Entry entry = new Entry();
		Title title = new Title();
		title.setType("text");
		title.setTitle(escenicEmbed.getTitle());
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
		embedDetailsFields.add(escenicUtils.createField("embedCode", escenicUtils.wrapWithCDATA(socialEmbed.getEmbedCode()), null, null));
		embedFields.add(escenicUtils.createField("socialEmbeds", null, embedDetailsFields, null));
		embedFields.add(escenicUtils.createField("title", escenicEmbed.getTitle(), null, null));
		p.setField(embedFields);
		com.atex.onecms.app.dam.integration.camel.component.escenic.model.List list = new com.atex.onecms.app.dam.integration.camel.component.escenic.model.List();
		list.setPayload(p);
		fields.add(escenicUtils.createField("socialEmbeds", null, null, list));
		fields.add(escenicUtils.createField("title", socialEmbed.getSocialNetwork(), null, null));
		return fields;
	}
}
