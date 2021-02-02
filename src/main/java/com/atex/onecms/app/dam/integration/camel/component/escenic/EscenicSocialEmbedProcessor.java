package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicResponseException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToExtractLocationException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToSendContentToEscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Entry;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Field;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Link;
import com.atex.onecms.app.dam.standard.aspects.DamEmbedAspectBean;
import com.atex.onecms.content.*;
import com.polopoly.cm.client.CMException;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class EscenicSocialEmbedProcessor extends EscenicSmartEmbedProcessor {

	private static EscenicSocialEmbedProcessor instance;
	private static final CustomEmbedParser customEmbedParser = new CustomEmbedParser();
	private static final Logger LOGGER = Logger.getLogger(EscenicSocialEmbedProcessor.class.getName());

	public EscenicSocialEmbedProcessor(EscenicUtils escenicUtils) {
		super(escenicUtils);
	}

	public static EscenicSocialEmbedProcessor getInstance() {
		if (instance == null) {
			throw new RuntimeException("EscenicSocialEmbedProcessor not initialized");
		}
		return instance;
	}

	public static void initInstance(EscenicUtils escenicUtils) {
		if (instance == null) {
			instance = new EscenicSocialEmbedProcessor(escenicUtils);
		}
	}

    private void assingEmbedProperties(DamEmbedAspectBean embedBean,
                                       ContentId contentId,
                                       String existingEscenicLocation,
                                       String escenicId,
                                       String embedUrl,
                                       EscenicEmbed escenicEmbed,
                                       Websection websection) {
	    
		escenicEmbed.setEscenicLocation(existingEscenicLocation);
		escenicEmbed.setEscenicId(escenicId);
		escenicEmbed.setTitle(embedBean.getName());
		escenicEmbed.setEmbedUrl(embedUrl);
		escenicEmbed.setOnecmsContentId(contentId);
		List<Link> links = escenicUtils.generateLinks(escenicEmbed, websection);
		escenicEmbed.setLinks(links);
	}

	protected EscenicEmbed processSocialEmbed(CustomEmbedParser.SmartEmbed embed, Websection websection) throws IOException, FailedToSendContentToEscenicException {
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
					existingEscenicLocation = getEscenicIdFromEngagement(contentId);
				} catch (CMException e) {
					e.printStackTrace();
				}

				DamEmbedAspectBean embedBean = null;
				try {
					embedBean = (DamEmbedAspectBean) escenicUtils.extractContentBean(embedCr);
				} catch (Exception e) {
					throw new RuntimeException("Failed to retrieve DamEmbedAspect bean for : " + embedCr.getContentId());
				}

				String escenicId = escenicUtils.extractIdFromLocation(existingEscenicLocation);
				if (StringUtils.isNotEmpty(escenicId)) {

					assingEmbedProperties(embedBean, contentId, existingEscenicLocation, escenicId, embed.getEmbedUrl(), escenicEmbed, websection);
					final EngagementDesc engagement = createEngagementObject(escenicId);
					processEngagement(escenicEmbed.getOnecmsContentId(), engagement, escenicEmbed.getEscenicLocation(), embedCr);
				}
			}
		} else {
			//brand new embed that does not exist in escenic
			try (CloseableHttpResponse response = EscenicSmartEmbedProcessor.getInstance().processEmbed(embed, escenicEmbed, websection)) {
				int statusCode = escenicUtils.getResponseStatusCode(response);
				if (statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NO_CONTENT) {
					String escenicLocation = null;
					try {
						escenicLocation = retrieveEscenicLocation(response);
					} catch (FailedToExtractLocationException e) {
						e.printStackTrace();
					}
					String escenicId = escenicUtils.extractIdFromLocation(escenicLocation);
					final EngagementDesc engagement = createEngagementObject(escenicId);
					embedCr = createOnecmsEmbed(escenicEmbed);
					DamEmbedAspectBean embedBean = null;
					try {
						embedBean = (DamEmbedAspectBean) escenicUtils.extractContentBean(embedCr);
					} catch (Exception e) {
						throw new RuntimeException("Failed to retrieve DamEmbedAspect bean for : " + embedCr.getContentId());
					}
					ContentId onecmsId = embedCr.getContent().getId().getContentId();
					assingEmbedProperties(embedBean, onecmsId, escenicLocation, escenicId, embed.getEmbedUrl(), escenicEmbed, websection );
					processEngagement(escenicEmbed.getOnecmsContentId(), engagement, null, embedCr);
				} else {
					LOGGER.severe("The server returned : " + response.getStatusLine() + " when attempting to send embed id: " + escenicEmbed.getOnecmsContentId());
					throw new RuntimeException("Received an error response from escenic: " + response.getStatusLine().getStatusCode() + " : " + response.getStatusLine().getReasonPhrase() + " - " + response.getEntity().toString());
				}
			} catch (EscenicResponseException e) {
				e.printStackTrace();
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
		embedAspectBean.setName(escenicEmbed.getTitle());
		//changing the object type so that embeds are not visible/searchable in desk
		embedAspectBean.setObjectType("escenicEmbed");

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
		final String body = customEmbedParser.processSmartEmbedToHtml(structuredText, true, (e) -> {
			final CustomEmbedParser.SmartEmbed embed = customEmbedParser.createSmartEmbedFromElement(e);
			if (embed != null) {
				for (EscenicContent escenicContent : escenicContentList) {
					if (escenicContent.isInlineElement()) {
						if (escenicContent instanceof EscenicImage || escenicContent instanceof EscenicGallery) {

							if (escenicContent.getOnecmsContentId() != null) {
								if (StringUtils.isNotEmpty(escenicContent.getEscenicId()) && embed.getContentId() != null &&
									StringUtils.equalsIgnoreCase(IdUtil.toIdString(escenicContent.getOnecmsContentId()), IdUtil.toIdString(embed.getContentId()))) {
									e.attr("escenic-id", escenicContent.getEscenicId());
								}
							} else {
								throw new RuntimeException("EscenicContent onemcsid was null");
							}
						}

						if (escenicContent instanceof EscenicEmbed) {
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

	protected String replaceEmbeds(String structuredText, List<EscenicContent> escenicContentList) {
		final String body = customEmbedParser.processSmartEmbedToHtml(structuredText, false, (e) -> {
			final CustomEmbedParser.SmartEmbed embed = customEmbedParser.createSmartEmbedFromElement(e);
			if (embed != null) {

				for (EscenicContent escenicContent : escenicContentList) {
					switch (embed.getObjType()) {

						case EscenicImage.IMAGE_TYPE:
							if (escenicContent instanceof EscenicImage) {
								final ContentId id = embed.getContentId();
								if (id != null && escenicContent.getOnecmsContentId() != null) {
									if (id.equals(escenicContent.getOnecmsContentId())) {
										try {
											EscenicImage escenicImage = (EscenicImage) escenicContent;
											if (!escenicImage.isNoUseWeb()) {
												if (StringUtils.isEmpty(escenicImage.getId())) {
													escenicImage.setId("_" + UUID.randomUUID().toString());
												}
												String newHtml = "<p><img src=\"" + escenicImage.getThumbnailUrl() + "\" alt=\"undefined\" id=\"" + escenicImage.getId() + "\"></img></p>";
												final Element ne = Jsoup.parseBodyFragment(newHtml).body().child(0);
												e.replaceWith(ne);
											} else {
												e.remove();
											}
										} catch (ClassCastException classCastException) {
											throw new RuntimeException("Error occurred while attempting to cast EscenicContent to EscenicImage: " + classCastException.getMessage());
										}
									}
								}
							}
							break;

						case EscenicGallery.GALLERY_TYPE:

							if (escenicContent instanceof EscenicGallery) {
								final ContentId id = embed.getContentId();
								if (id != null && escenicContent.getOnecmsContentId() != null) {
									if (id.equals(escenicContent.getOnecmsContentId())) {

										try {
											EscenicGallery escenicGallery = (EscenicGallery) escenicContent;
											if (StringUtils.isEmpty(escenicGallery.getId())) {
												escenicGallery.setId("_" + UUID.randomUUID().toString());
											}
											String newHtml = "<p><a href=\"" + escenicGallery.getEscenicLocation() + "\" id=\"" + escenicGallery.getId() + "\" alt=\"undefined\">" +
												escenicUtils.escapeXml(escenicGallery.getTitle()) + "</a></p>";

											final Element ne = Jsoup.parseBodyFragment(newHtml).body().child(0);
											e.replaceWith(ne);
										} catch (ClassCastException classCastException) {
											throw new RuntimeException("Error occurred while attempting to cast EscenicContent to EscenicImage: " + classCastException.getMessage());
										}
									}
								}
							}
							break;

						case EscenicContentReference.CONTENT_REFERENCE_GENERAL_TYPE:
						case EscenicContentReference.CONTENT_REFERENCE_VIDEO_TYPE:

							if (escenicContent instanceof EscenicContentReference) {

								final ContentId id = embed.getContentId();
								if (id != null && escenicContent.getOnecmsContentId() != null) {
									if (id.equals(escenicContent.getOnecmsContentId())) {
										EscenicContentReference escenicContentReference = (EscenicContentReference) escenicContent;
										if (StringUtils.isNotEmpty(escenicContentReference.getType())) {
											String newHtml = "";
											if (StringUtils.isEmpty(escenicContentReference.getId())) {
												escenicContentReference.setId("_" + UUID.randomUUID().toString());
											}
											switch (escenicContentReference.getType()) {
												case EscenicContentReference.ESCENIC_ARTICLE_TYPE:
												case EscenicContentReference.ESCENIC_VIDEO_TYPE:
												case EscenicContentReference.ESCENIC_CODE_TYPE:
												case EscenicContentReference.ESCENIC_SOUNDCLOUD_TYPE:
												case EscenicContentReference.ESCENIC_GALLERY_TYPE:
												case EscenicContentReference.ESCENIC_SOCIAL_EMBED_TYPE:
													newHtml = "<p><a href=\"" + escenicContentReference.getEscenicLocation() + "\" id=\"" + escenicContentReference.getId() + "\">" +
														escenicUtils.escapeXml(escenicContentReference.getTitle()) + "</a></p>";
													break;
												case EscenicContentReference.ESCENIC_IMAGE_TYPE:
													newHtml = "<p><img src=\"" + escenicContentReference.getThumbnailUrl() + "\" alt=\"undefined\" id=\"" + escenicContentReference.getId() + "\"></img></p>";
													break;
												default:
													//strip the element off...
													newHtml = "<span></span>";
													break;

											}

											final Element ne = Jsoup.parseBodyFragment(newHtml).body().child(0);
  											e.replaceWith(ne);
										}
									}
								}
							}

							break;

						case EscenicEmbed.SOCIAL_EMBED_TYPE:
							if (escenicContent instanceof EscenicEmbed) {

								final ContentId id = embed.getContentId();
								if (id != null && escenicContent.getOnecmsContentId() != null) {
									if (id.equals(escenicContent.getOnecmsContentId())) {
										EscenicEmbed escenicEmbed = (EscenicEmbed) escenicContent;
										if (StringUtils.isNotEmpty(embed.getEmbedUrl()) && StringUtils.equalsIgnoreCase(escenicEmbed.getEmbedUrl(), embed.getEmbedUrl())) {
											if (StringUtils.equalsIgnoreCase(embed.getEscenicId(), escenicEmbed.getEscenicId())) {
												if (StringUtils.isEmpty(escenicEmbed.getId())) {
													escenicEmbed.setId("_" + UUID.randomUUID().toString());
												}
												String newHtml = "<p><a href=\"" + escenicEmbed.getEscenicLocation() + "\" id=\"" + escenicEmbed.getId() + "\">" +
													 escenicUtils.escapeXml(escenicEmbed.getTitle()) + "</a></p>";

												final Element ne = Jsoup.parseBodyFragment(newHtml).body().child(0);
												e.replaceWith(ne);
											}
										}
									}
								}
							}
							break;
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
								LOGGER.severe("was unable to process internal id within article body");
							}
						}
					}
				}
			}
		}
	}
}
