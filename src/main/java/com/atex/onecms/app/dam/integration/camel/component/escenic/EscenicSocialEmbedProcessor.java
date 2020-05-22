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
	protected EscenicEmbed processSocialEmbed(CustomEmbedParser.SmartEmbed embed, DamEngagementUtils utils, ContentId articleContentId, String sectionId) throws IOException, URISyntaxException, FailedToSendContentToEscenicException {
		ContentId contentId = null;
		if (embed != null && embed.getContentId() != null) {
			contentId = embed.getContentId();
		}
		/**
		 * TODO issue here is that we do not currently store the embeds...
		 *
		 * create it as a content ? -> so that we can then update the engagmenet.
		 * Do we need to update the body of the article with updated html that contains onecms-id of embed object for further processing?
		 */
//		for (CustomEmbedParser.SocialEmbed embed : socialEmbeds) {
		ContentResult<DamEmbedAspectBean> embedCr = null;
		EscenicEmbed escenicEmbed = new EscenicEmbed();
		if (contentId != null) {
			//TODO
			//NOT NECESSARILY means we're dealing with embed that has already been published...

			ContentVersionId contentVersionId = contentManager.resolve(contentId, Subject.NOBODY_CALLER);
			if (contentVersionId != null) {
				embedCr = contentManager.get(contentVersionId, null, DamEmbedAspectBean.class, null, Subject.NOBODY_CALLER);
			}

			if (embedCr != null && embedCr.getStatus().isSuccess()) {
				//check if the enagement on the image exists?
				//we'll need to detect the filename used? just in case the user removes and image and uploads a different one for the same desk object..
				//this could cause a problem on the escenic side if the image was already uploaded (the binary file)
				String existingEscenicLocation = null;
				try {
					existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
				} catch (CMException e) {
					e.printStackTrace();
				}

				String escenicId = extractIdFromLocation(existingEscenicLocation);
				if (StringUtils.isNotEmpty(escenicId)) {
					escenicEmbed.setEscenicLocation(existingEscenicLocation);
					escenicEmbed.setEscenicId(escenicId);
					escenicEmbed.setEmbedCode(embed.getEmbedCode());
					escenicEmbed.setEmbedUrl(embed.getEmbedUrl());
					contentId = createOnecmsEmbed(escenicEmbed );
					escenicEmbed.setOnecmsContentId(contentId);
					List<Link> links = escenicUtils.generateLinks(escenicEmbed);
					escenicEmbed.setLinks(links);
				}
			}
		} else {
			//brand new embed that does not exist in escenic
			List<CloseableHttpResponse> responses;
			responses = EscenicSmartEmbedProcessor.getInstance().processEmbed(embed, cmServer, escenicConfig, escenicEmbed, sectionId);
			for (CloseableHttpResponse response : responses) {
				int statusCode = response.getStatusLine().getStatusCode();
				//|| statusCode == HttpStatus.SC_NO_CONTENT
				if (statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_OK) {
					String escenicLocation = null;
					try {
						escenicLocation = retrieveEscenicLocation(response);
					} catch (FailedToExtractLocationException e) {
						e.printStackTrace();
					}
					String escenicId = extractIdFromLocation(escenicLocation);
					final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
					escenicEmbed.setEscenicLocation(escenicLocation);
					escenicEmbed.setEscenicId(escenicId);
					escenicEmbed.setEmbedCode(embed.getEmbedCode());
					escenicEmbed.setEmbedUrl(embed.getEmbedUrl());
					contentId = createOnecmsEmbed(escenicEmbed );
					escenicEmbed.setOnecmsContentId(contentId);
					List<Link> links = escenicUtils.generateLinks(escenicEmbed);
					escenicEmbed.setLinks(links);

					processEngagement(contentId, engagement, null, utils, embedCr);


				} else {
					log.error("The server returned : " + response.getStatusLine() + " when attempting to send embed id: " + escenicEmbed.getOnecmsContentId());
				}
			}
		}

		return escenicEmbed;
	}



	/**
	 * responsible for creating dummy onecms Embeds for the purpose of storing engagements
	 * @param escenicEmbed
	 */
	protected ContentId createOnecmsEmbed(EscenicEmbed escenicEmbed) {
		escenicEmbed.getEscenicId();
		escenicEmbed.getEmbedCode();
		escenicEmbed.getEscenicLocation();

		DamEmbedAspectBean embedAspectBean = new DamEmbedAspectBean();
		embedAspectBean.setHtml(escenicEmbed.getEmbedCode());
		//todo parse the name based on the url...
		embedAspectBean.setName(escenicEmbed.getEscenicId());

		ContentWrite<DamEmbedAspectBean> content  = new ContentWriteBuilder<DamEmbedAspectBean>()
			.type(DamEmbedAspectBean.ASPECT_NAME)
			.mainAspectData(embedAspectBean)
			.buildCreate();

		if (content != null) {
			ContentResult<DamEmbedAspectBean> cr = contentManager.create(content, SubjectUtil.fromCaller(getCurrentCaller()));
			return cr.getContent().getId().getContentId();

		} else {
			throw new RuntimeException("failed to create DamEmbedAspectBean");
		}
	}

	protected static String addOnecmsIdToSocialEmbeds(String structuredText, List<EscenicContent> escenicContentList) {
		final String body = customEmbedParser.processSmartEmbedToHtml(structuredText, (e) -> {
			final CustomEmbedParser.SmartEmbed embed = customEmbedParser.createSmartEmbedFromElement(e);
			if (embed != null) {
				for (EscenicContent escenicContent : escenicContentList) {
					if (escenicContent.isInlineElement()) {
						if (escenicContent != null && (escenicContent instanceof EscenicImage || escenicContent instanceof EscenicGallery)) {
//						||	escenicContent instanceof EscenicVideo)

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

	protected static String replaceEmbeds(String structuredText, List<EscenicContent> escenicContentList) {
		final String body = customEmbedParser.processSmartEmbedToHtml(structuredText, (e) -> {
			final CustomEmbedParser.SmartEmbed embed = customEmbedParser.createSmartEmbedFromElement(e);
			if (embed != null) {

				for (EscenicContent escenicContent : escenicContentList) {
					switch (embed.getObjType()) {

						case EscenicImage.IMAGE_TYPE:
							//todo here check the embed type and generate different html tags based on that.?
							if (escenicContent != null && escenicContent instanceof EscenicImage) {
								final ContentId id = embed.getContentId();
								if (id != null && escenicContent.getOnecmsContentId() != null) {
									if (id.equals(escenicContent.getOnecmsContentId())) {

										try {
											EscenicImage escenicImage = (EscenicImage) escenicContent;
											String newHtml;
											if (StringUtils.isNotEmpty(escenicImage.getId())) {
												newHtml = "<p><img src=\"" + escenicImage.getThumbnailUrl() + "\" alt=\"undefined\" id=\"" + escenicImage.getId() + "\"></img></p>";
											} else {
												newHtml = "<p><img src=\"" + escenicImage.getThumbnailUrl() + "\" alt=\"undefined\"></img></p>";
											}

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
											String newHtml;
											if (StringUtils.isNotEmpty(escenicGallery.getId())) {
												newHtml = "<p><a href=\"" + escenicGallery.getEscenicLocation() + "\" id=\"" + escenicGallery.getId() + "\" alt=\"undefined\"></a></p>";
											} else {
												newHtml = "<p><a href=\"" + escenicGallery.getEscenicLocation() + "\" alt=\"undefined\"></a></p>";
											}

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
									String newHtml;
									if (StringUtils.isNotEmpty(escenicEmbed.getId())) {
										newHtml = "<p><a href=\"" + escenicEmbed.getEscenicLocation() + "\" id=\"" + escenicEmbed.getId() + "\"></a></p>";
									} else {
										newHtml = "<p><a href=\"" + escenicEmbed.getEscenicLocation() + "\"></a></p>";
									}

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
											if (e.hasAttr("id") && !e.attr("id").startsWith("_") && (escenicContent.getId() == null || !escenicContent.getId().startsWith("_"))) {
												escenicContent.setId("_" + e.attr("id"));
											} else if (e.hasAttr("id") && e.attr("id").startsWith("_") && (escenicContent.getId() == null || !escenicContent.getId().startsWith("_"))) {
												escenicContent.setId(e.attr("id"));
											}
										}
									}
								}
							} else {
								log.debug("");
							}
						}
					}
				}
			}
		}
	}


}
