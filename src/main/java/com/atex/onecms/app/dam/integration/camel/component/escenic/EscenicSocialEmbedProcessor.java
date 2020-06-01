package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToExtractLocationException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToSendContentToEscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Entry;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Field;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Link;
import com.atex.onecms.app.dam.standard.aspects.DamEmbedAspectBean;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.content.*;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.PolicyCMServer;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

public class EscenicSocialEmbedProcessor extends EscenicSmartEmbedProcessor {

	private static EscenicSocialEmbedProcessor instance;
	private static final CustomEmbedParser customEmbedParser = new CustomEmbedParser();

	public EscenicSocialEmbedProcessor(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
		super(contentManager, cmServer, escenicUtils, escenicConfig);
	}

	public static EscenicSocialEmbedProcessor getInstance() {
		if (instance == null) {
			throw new RuntimeException("EscenicContentProcessor not initialized");
		}
		return instance;
	}

	public static void initInstance(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
		if (instance == null) {
			instance = new EscenicSocialEmbedProcessor(contentManager, cmServer, escenicUtils, escenicConfig);
		}
	}

	private void assingEmbedProperties(DamEmbedAspectBean embedBean, ContentId contentId, String existingEscenicLocation, String escenicId, String embedCode, String embedUrl, EscenicEmbed escenicEmbed) {
		escenicEmbed.setEscenicLocation(existingEscenicLocation);
		escenicEmbed.setEscenicId(escenicId);
		escenicEmbed.setTitle(embedBean.getName());
		escenicEmbed.setEmbedCode(embedCode);
		escenicEmbed.setEmbedUrl(embedUrl);
		escenicEmbed.setOnecmsContentId(contentId);
		List<Link> links = escenicUtils.generateLinks(escenicEmbed);
		escenicEmbed.setLinks(links);
	}

	protected EscenicEmbed processSocialEmbed(CustomEmbedParser.SmartEmbed embed, DamEngagementUtils utils, ContentId articleContentId, String sectionId, String action) throws IOException, URISyntaxException, FailedToSendContentToEscenicException {
		ContentId contentId = null;
		if (embed != null && embed.getContentId() != null) {
			contentId = embed.getContentId();
		}

		ContentResult<DamEmbedAspectBean> embedCr = null;
		EscenicEmbed escenicEmbed = new EscenicEmbed();
		if (contentId != null) {
			ContentVersionId contentVersionId = contentManager.resolve(contentId, Subject.NOBODY_CALLER);
			if (contentVersionId != null) {
				embedCr = contentManager.get(contentVersionId, null, DamEmbedAspectBean.class, null, Subject.NOBODY_CALLER);
			}

			if (embedCr != null && embedCr.getStatus().isSuccess()) {
				String existingEscenicLocation = null;
				try {
					existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
				} catch (CMException e) {
					e.printStackTrace();
				}

				DamEmbedAspectBean embedBean = null;
				try {
					embedBean = (DamEmbedAspectBean) escenicUtils.extractContentBean(embedCr);
				} catch (Exception e) {
					throw new RuntimeException("Failed to retrieve DamEmbedAspect bean for : " + embedCr.getContentId());
				}

				String escenicId = extractIdFromLocation(existingEscenicLocation);
				if (StringUtils.isNotEmpty(escenicId)) {

					assingEmbedProperties(embedBean, contentId, existingEscenicLocation, escenicId, embed.getEmbedCode(), embed.getEmbedUrl(), escenicEmbed);
					final EngagementDesc engagement = createEngagementObject(escenicId, existingEscenicLocation, getCurrentCaller());
					processEngagement(escenicEmbed.getOnecmsContentId(), engagement, escenicEmbed.getEscenicLocation(), utils, embedCr);
				}
			}
		} else {
			//brand new embed that does not exist in escenic
			CloseableHttpResponse response = EscenicSmartEmbedProcessor.getInstance().processEmbed(embed, escenicConfig, escenicEmbed, sectionId);

			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NO_CONTENT) {
				String escenicLocation = null;
				try {
					escenicLocation = retrieveEscenicLocation(response);
				} catch (FailedToExtractLocationException e) {
					e.printStackTrace();
				}
				String escenicId = extractIdFromLocation(escenicLocation);
				final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
				ContentResult cr = createOnecmsEmbed(escenicEmbed);
				DamEmbedAspectBean embedBean = null;
				try {
					 embedBean = (DamEmbedAspectBean) escenicUtils.extractContentBean(cr);
				} catch (Exception e) {
					throw new RuntimeException("Failed to retrieve DamEmbedAspect bean for : " + cr.getContentId());
				}
				ContentId onecmsId = cr.getContent().getId().getContentId();
				assingEmbedProperties(embedBean, onecmsId, escenicLocation, escenicId, embed.getEmbedCode(), embed.getEmbedUrl(), escenicEmbed );
				processEngagement(escenicEmbed.getOnecmsContentId(), engagement, null, utils, embedCr);
			} else {
				log.error("The server returned : " + response.getStatusLine() + " when attempting to send embed id: " + escenicEmbed.getOnecmsContentId());
				throw new RuntimeException("Received an error response from escenic: " + response.getStatusLine().getStatusCode() + " : " + response.getStatusLine().getReasonPhrase() + " - " + response.getEntity().toString());

			}

		}

		return escenicEmbed;
	}

	/**
	 * responsible for creating dummy onecms Embeds for the purpose of storing engagements
	 * @param escenicEmbed
	 */
	protected ContentResult createOnecmsEmbed(EscenicEmbed escenicEmbed) throws RuntimeException {
		DamEmbedAspectBean embedAspectBean = new DamEmbedAspectBean();
		embedAspectBean.setHtml(escenicEmbed.getEmbedCode());
		embedAspectBean.setName(escenicEmbed.getEscenicId());

		ContentWrite<DamEmbedAspectBean> content  = new ContentWriteBuilder<DamEmbedAspectBean>()
			.type(DamEmbedAspectBean.ASPECT_NAME)
			.mainAspectData(embedAspectBean)
			.buildCreate();

		if (content != null) {
			ContentResult<DamEmbedAspectBean> cr = contentManager.create(content, SubjectUtil.fromCaller(getCurrentCaller()));
			return cr;

		} else {
			throw new RuntimeException("Failed to create DamEmbedAspectBean");
		}
	}

	protected static String addOnecmsIdToSocialEmbeds(String structuredText, List<EscenicContent> escenicContentList) {
		final String body = customEmbedParser.processSmartEmbedToHtml(structuredText, (e) -> {
			final CustomEmbedParser.SmartEmbed embed = customEmbedParser.createSmartEmbedFromElement(e);
			if (embed != null) {
				for (EscenicContent escenicContent : escenicContentList) {
					if (escenicContent.isInlineElement()) {
						if (escenicContent != null && (escenicContent instanceof EscenicImage || escenicContent instanceof EscenicGallery)) {
						//todo drop2 -> ||	escenicContent instanceof EscenicVideo)  ?

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

	public static List<CustomEmbedParser.SmartEmbed> processEmbeds(String text) {
		return customEmbedParser.getSmartEmbed(text);
	}

	protected static String replaceEmbeds(String structuredText, List<EscenicContent> escenicContentList) {
		final String body = customEmbedParser.processSmartEmbedToHtml(structuredText, (e) -> {
			final CustomEmbedParser.SmartEmbed embed = customEmbedParser.createSmartEmbedFromElement(e);
			if (embed != null) {

				for (EscenicContent escenicContent : escenicContentList) {
					switch (embed.getObjType()) {

						case EscenicImage.IMAGE_TYPE:
							if (escenicContent != null && escenicContent instanceof EscenicImage) {
								final ContentId id = embed.getContentId();
								if (id != null && escenicContent.getOnecmsContentId() != null) {
									if (id.equals(escenicContent.getOnecmsContentId())) {

										try {
											EscenicImage escenicImage = (EscenicImage) escenicContent;
											if (StringUtils.isEmpty(escenicImage.getId())) {
												escenicImage.setId("_" + UUID.randomUUID().toString());
											}
											String newHtml = "<p><img src=\"" + escenicImage.getThumbnailUrl() + "\" alt=\"undefined\" id=\"" + escenicImage.getId() + "\"></img></p>";
											final Element ne = Jsoup.parseBodyFragment(newHtml).body().child(0);
											e.replaceWith(ne);
										} catch (ClassCastException classCastException) {
											throw new RuntimeException("Error occurred while attempting to cast EscenicContent to EscenicImage: " + classCastException.getMessage());
										}
									}
								}
							}
							break;

						case EscenicGallery.GALLERY_TYPE:

							if (escenicContent != null && escenicContent instanceof EscenicGallery) {
								final ContentId id = embed.getContentId();
								if (id != null && escenicContent.getOnecmsContentId() != null) {
									if (id.equals(escenicContent.getOnecmsContentId())) {

										try {
											EscenicGallery escenicGallery = (EscenicGallery) escenicContent;
											if (StringUtils.isEmpty(escenicGallery.getId())) {
												escenicGallery.setId("_" + UUID.randomUUID().toString());
											}
											String newHtml = "<p><a href=\"" + escenicGallery.getEscenicLocation() + "\" id=\"" + escenicGallery.getId() + "\" alt=\"undefined\"></a></p>";

											final Element ne = Jsoup.parseBodyFragment(newHtml).body().child(0);
											e.replaceWith(ne);
										} catch (ClassCastException classCastException) {
											throw new RuntimeException("Error occurred while attempting to cast EscenicContent to EscenicImage: " + classCastException.getMessage());
										}
									}
								}
							}
							break;

						case "video":

							//TODO drop 2
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

						case EscenicEmbed.SOCIAL_EMBED_TYPE:
							if (escenicContent != null && escenicContent instanceof EscenicEmbed) {
								EscenicEmbed escenicEmbed = (EscenicEmbed) escenicContent;
								if (StringUtils.isNotEmpty(embed.getEmbedUrl()) && StringUtils.equalsIgnoreCase(escenicEmbed.getEmbedUrl(), embed.getEmbedUrl())) {

									if (StringUtils.isEmpty(escenicEmbed.getId())) {
										escenicEmbed.setId("_" + UUID.randomUUID().toString());
									}
									String newHtml = "<p><a href=\"" + escenicEmbed.getEscenicLocation() + "\" id=\"" + escenicEmbed.getId() + "\"></a></p>";

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

	protected void updateIdsForEscenicContent(Entry entry, List<EscenicContent> escenicContentList) {

		if (escenicContentList != null) {
			for (EscenicContent escenicContent : escenicContentList) {
				if (escenicContent != null) {
					for (Field field : entry.getContent().getPayload().getField()) {

						if (field != null && StringUtils.equalsIgnoreCase(field.getName(), "body")) {

							if (field.getValue() != null && field.getValue().getValue() != null) {
								for (Object o : field.getValue().getValue()) {

									List<Element> elements = customEmbedParser.processBodyFromEscenic(o.toString());

									for (Element e : elements) {
										if (StringUtils.isNotEmpty(escenicContent.getEscenicLocation()) &&
											(e.hasAttr("href") && StringUtils.equalsIgnoreCase(escenicContent.getEscenicLocation(), e.attr("href")) ||
												(e.hasAttr("src") &&
													StringUtils.equalsIgnoreCase(escenicContent.getEscenicLocation().replaceAll("escenic/content", "thumbnail/article"), e.attr("src"))))) {
											//left this to ensure the id is prefixed with_
											if (e.hasAttr("id") && !e.attr("id").startsWith("_") && (escenicContent.getId() == null || !escenicContent.getId().startsWith("_"))) {
												escenicContent.setId("_" + e.attr("id"));
											} else if (e.hasAttr("id") && e.attr("id").startsWith("_") && (escenicContent.getId() == null || !escenicContent.getId().startsWith("_"))) {
												escenicContent.setId(e.attr("id"));
											}
										}
									}
								}
							} else {
								log.error("was unable to process internal id within article body");
							}
						}
					}
				}
			}
		}
	}
}
