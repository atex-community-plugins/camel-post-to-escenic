package com.atex.onecms.app.dam.integration.camel.component.escenic;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.IdUtil;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;

	/**
	 * Parse a body text and extract smart embeds and social embeds
	 *
	 * @author jakub
	 *
	 */
	public class CustomEmbedParser {

		private static final Logger LOGGER = Logger.getLogger(com.atex.standard.article.SmartEmbedParser.class.getName());

		private static final String DATA_ONECMS_TYPE = "data-onecms-type";
		private static final String DATA_ONECMS_ID = "data-onecms-id";
		private static final String DATA_OEMBED_URL = "data-oembed-url";

		public List<CustomEmbedParser.SmartEmbed> getSmartEmbed(final String html) {
			final List<CustomEmbedParser.SmartEmbed> results = Lists.newArrayList();
			processSmartEmbed(html, (e) -> {
				final CustomEmbedParser.SmartEmbed embed = createSmartEmbedFromElement(e);
				if (embed != null) {
					results.add(embed);
				}
			});
			return results;
		}

		public Document processSmartEmbed(final String html, final Consumer<Element> c) {
			final Document doc = Jsoup.parseBodyFragment(Strings.nullToEmpty(html));
			for (final Element element : doc.select("div.p-smartembed")) {
				c.accept(element);
			}
			for (final Element element : doc.select("a.p-smartembed")) {
				c.accept(element);
			}
			for (final Element element : doc.select("img.p-smartembed")) {
				c.accept(element);
			}
			for (final Element element : doc.select("div")) {
				if (element.hasAttr("data-oembed-url")) {
					c.accept(element);
				}
			}

			return doc;
		}

		public List<Element> processBodyFromEscenic(final String html) {
			final Document doc = Jsoup.parseBodyFragment(Strings.nullToEmpty(html));
			List<Element> elementsList = new ArrayList<>();
			for (final Element element : doc.select("a")) {
				elementsList.add(element);
			}
			for (final Element element : doc.select("img")) {
				elementsList.add(element);
			}

			return elementsList;
		}

		public String processSmartEmbedToHtml(final String html, final boolean disablePrettyPrint, final Consumer<Element> c) {
			final Document doc = processSmartEmbed(html, c);

			if (disablePrettyPrint) {
				doc.outputSettings().escapeMode(Entities.EscapeMode.base);
				doc.outputSettings().prettyPrint(false);
			} else {
				doc.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);
			}

			return doc.body().html();
		}

		public CustomEmbedParser.SmartEmbed createSmartEmbedFromElement(final Element element) {
			// check if the element have the required attributes
			if (element.hasAttr(DATA_ONECMS_TYPE) && element.hasAttr(DATA_ONECMS_ID)) {
				final CustomEmbedParser.SmartEmbed embed = new CustomEmbedParser.SmartEmbed();
				embed.setObjType(element.attr(DATA_ONECMS_TYPE));

				if (element.hasAttr("escenic-id")) {
					embed.setEscenicId(element.attr("escenic-id").trim());
				}

				// try to get an id (the element may have not been created yet).
				final String id = element.attr(DATA_ONECMS_ID).trim();
				if (!Strings.isNullOrEmpty(id)) {
					try {
						final ContentId contentId = IdUtil.fromString(id);
						embed.setContentId(contentId);
					} catch (IllegalArgumentException e) {
						LOGGER.warning(e.getMessage());
					}
				}

				// get the list of all the other data attributes.
				for (final Attribute attribute : element.attributes()) {
					final String key = attribute.getKey();
					if (key.equals(DATA_ONECMS_TYPE) || key.equals(DATA_ONECMS_ID)) {
						continue;
					}
					if (key.startsWith("data-") || key.startsWith("polopoly:")) {
						embed.getAttributes().put(key, attribute.getValue());
					}
				}

				// finally get the element text
				embed.setContent(element.html());

				return embed;
			} else {
				if (element.hasAttr(DATA_OEMBED_URL)) {
					final CustomEmbedParser.SmartEmbed embed = new CustomEmbedParser.SmartEmbed();
					embed.setEmbedUrl(element.attr(DATA_OEMBED_URL).trim());
					embed.setObjType("socialEmbed");
					if (element.hasAttr("escenic-id")) {
						embed.setEscenicId(element.attr("escenic-id").trim());
					}

					if (element.hasAttr(DATA_ONECMS_ID)) {
						final String id = element.attr(DATA_ONECMS_ID).trim();
						if (!Strings.isNullOrEmpty(id)) {
							try {
								final ContentId contentId = IdUtil.fromString(id);
								embed.setContentId(contentId);
							} catch (IllegalArgumentException e) {
								LOGGER.warning(e.getMessage());
							}
						}
					}

					// get the list of all the other data attributes.
					for (final Attribute attribute : element.attributes()) {
						final String key = attribute.getKey();
						if (key.equals(DATA_OEMBED_URL)) {
							continue;
						}
						if (key.startsWith("data-") || key.startsWith("polopoly:")) {
							embed.getAttributes().put(key, attribute.getValue());
						}
					}

					if (StringUtils.isNotEmpty(embed.getEmbedUrl())) {
						try {
							String networkName = extractSocialNetworkFromUrl(embed.getEmbedUrl());
							if (StringUtils.isNotEmpty(networkName)) {
								embed.setSocialNetwork(networkName.toLowerCase());
							} else {

								return null;
							}

						} catch (URISyntaxException e) {
							e.printStackTrace();
						}
					}

					// finally get the element text
					embed.setEmbedCode(element.toString());
					return embed;
				}
			}

			return null;
		}

		private String extractSocialNetworkFromUrl(String embedUrl) throws URISyntaxException {
			if (StringUtils.isNotEmpty(embedUrl)) {
				URI uri = new URI(embedUrl);
				String domain = uri.getHost();
				domain = domain.contains(".") ? StringUtils.substringBeforeLast(domain, ".") : domain;
				domain = domain.startsWith("www.") ? domain.substring(4) : domain;
				return validateSocialNetworkName(domain);
			}
			return null;
		}

		private String validateSocialNetworkName(String socialNetworkName) {
			if (StringUtils.isNotEmpty(socialNetworkName)) {
				switch (socialNetworkName) {
					case "youtu":

						return "youtube";
					default :
						return socialNetworkName;
				}
			}
			return socialNetworkName;
		}

		public class SmartEmbed {
			private ContentId contentId;
			private String objType;
			private String content;
			private Map<String, String> attributes = Maps.newHashMap();
			private String embedUrl;
			private String socialNetwork;
			private String embedCode;

			public String getEscenicId() {
				return escenicId;
			}

			public void setEscenicId(String escenicId) {
				this.escenicId = escenicId;
			}

			private String escenicId;


			public ContentId getContentId() {
				return contentId;
			}

			public void setContentId(final ContentId contentId) {
				this.contentId = contentId;
			}

			public String getObjType() {
				return objType;
			}

			public void setObjType(final String objType) {
				this.objType = objType;
			}

			public String getContent() {
				return content;
			}

			public String getEmbedUrl() {
				return embedUrl;
			}

			public void setEmbedUrl(String embedUrl) {
				this.embedUrl = embedUrl;
			}

			public String getSocialNetwork() {
				return socialNetwork;
			}

			public void setSocialNetwork(String socialNetwork) {
				this.socialNetwork = socialNetwork;
			}

			public String getEmbedCode() {
				return embedCode;
			}

			public void setEmbedCode(String embedCode) {
				this.embedCode = embedCode;
			}

			public void setContent(final String content) {
				this.content = content;
			}

			public Map<String, String> getAttributes() {
				return attributes;
			}

			public void setAttributes(final Map<String, String> attributes) {
				this.attributes = attributes;
			}
		}

	}

