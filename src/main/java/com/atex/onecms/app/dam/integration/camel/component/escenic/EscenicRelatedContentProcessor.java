package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Link;
import com.atex.onecms.app.dam.standard.aspects.*;
import com.atex.onecms.content.*;
import com.polopoly.cm.policy.PolicyCMServer;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author jakub
 */
public class EscenicRelatedContentProcessor extends EscenicContentProcessor {

		private static com.atex.onecms.app.dam.integration.camel.component.escenic.EscenicRelatedContentProcessor instance;
		protected static final Logger LOGGER = Logger.getLogger(com.atex.onecms.app.dam.integration.camel.component.escenic.EscenicRelatedContentProcessor.class.getName());

		public EscenicRelatedContentProcessor(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
			super(contentManager, cmServer, escenicUtils, escenicConfig);

		}

		public synchronized static EscenicRelatedContentProcessor getInstance() {
			if (instance == null) {
				throw new RuntimeException("EscenicRelatedContentProcessor not initialized");
			}
			return instance;
		}

		public synchronized static void initInstance(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
			if (instance == null) {
				instance = new EscenicRelatedContentProcessor(contentManager, cmServer, escenicUtils, escenicConfig);
			}
		}

		protected EscenicContentReference process(CustomEmbedParser.SmartEmbed embed) throws EscenicException {
			ContentId contentId = null;
			if (embed != null && embed.getContentId() != null) {
				contentId = embed.getContentId();
			}

			ContentResult<ExternalReferenceBean> externalReferenceCr = null;
			EscenicContentReference escenicContentReference = new EscenicContentReference();
			if (contentId != null) {
				ContentVersionId contentVersionId = contentManager.resolve(contentId, Subject.NOBODY_CALLER);
				if (contentVersionId != null) {
					externalReferenceCr = contentManager.get(contentVersionId, null, ExternalReferenceBean.class, null, SubjectUtil.fromCaller(getCurrentCaller()));
					ExternalReferenceBean externalReferenceBean;
					if (externalReferenceCr != null && externalReferenceCr.getStatus().isSuccess()) {

						try {
							externalReferenceBean = (ExternalReferenceBean) escenicUtils.extractContentBean(externalReferenceCr);
						} catch (Exception e) {
							LOGGER.severe("Failed to retrieve ExternalReferenceBean bean for : " + externalReferenceCr.getContentId());
							throw new EscenicException("An embedded link to a related article is linking to a Desk article, please fix and retry publish");
						}

						if (externalReferenceBean != null) {
							assignEscenicContentProperties(externalReferenceBean, contentId, escenicContentReference);
						}
					}
				} else {
					LOGGER.severe("unable to resolve content id: " + contentId);
				}
			} else {
				LOGGER.severe("Content id was blank for externally embedded content: " + embed);
			}

			return escenicContentReference;
		}

		private void assignEscenicContentProperties(ExternalReferenceBean externalReferenceBean, ContentId contentId, EscenicContentReference escenicContentReference) {

			if (StringUtils.isNotBlank(externalReferenceBean.getExternalReferenceContentType())) {
				escenicContentReference.setType(externalReferenceBean.getExternalReferenceContentType());
			}

			escenicContentReference.setEscenicLocation(externalReferenceBean.getLocation());
			escenicContentReference.setEscenicId(externalReferenceBean.getExternalReferenceId());
			escenicContentReference.setTitle(externalReferenceBean.getTitle());

			if (StringUtils.isNotBlank(externalReferenceBean.getThumbnailUrl())) {
				escenicContentReference.setThumbnailUrl(externalReferenceBean.getThumbnailUrl());
			}

			escenicContentReference.setOnecmsContentId(contentId);
			List<Link> links = escenicUtils.generateLinks(escenicContentReference);
			escenicContentReference.setLinks(links);

		}
}
