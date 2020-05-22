package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.gong.publish.PublishingBean;
import com.atex.onecms.app.dam.engagement.EngagementAspect;
import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.engagement.EngagementElement;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.*;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Entry;
import com.atex.onecms.app.dam.standard.aspects.*;
import com.atex.onecms.app.dam.types.TimeState;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.app.dam.workflow.WFContentStatusAspectBean;
import com.atex.onecms.app.dam.workflow.WFStatusBean;
import com.atex.onecms.content.*;
import com.atex.onecms.content.aspects.Aspect;
import com.atex.onecms.content.repository.ContentModifiedException;
import com.atex.onecms.image.ImageEditInfoAspectBean;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.PolicyCMServer;
import com.polopoly.user.server.Caller;
import com.polopoly.user.server.UserId;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;

public class EscenicContentProcessor {


	protected EscenicConfig escenicConfig;
	private Caller latestCaller = null;
	protected final Logger log = LoggerFactory.getLogger(getClass());
	protected ContentManager contentManager;
	protected static PolicyCMServer cmServer;
	protected EscenicUtils escenicUtils;

	private static final String ESCENIC_APPTYPE = "escenic";
	public static final String STATUS_ATTR_EMBARGO = "attr.embargo";
	public static final String STATUS_ATTR_ONLINE = "attr.online";
	public static final String STATUS_ATTR_UNPUBLISHED = "attr.removed";
	public static final String CONTENT_ID_ATTRRIBUTE_PREFIX = "contentId.";

	private static EscenicContentProcessor instance;

	public EscenicContentProcessor() {

	}

	public EscenicContentProcessor(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
		this.contentManager = contentManager;
		this.cmServer = cmServer;
		this.escenicUtils = escenicUtils;
		this.escenicConfig = escenicConfig;
	}

	public static synchronized EscenicContentProcessor getInstance() {
		if (instance == null) {
			throw new RuntimeException("EscenicContentProcessor not initialized");
		}
		return instance;
	}

	public static synchronized void initInstance(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig) {
		if (instance == null) {
			instance = new EscenicContentProcessor(contentManager, cmServer, escenicUtils, escenicConfig);
		}
	}

	public void processPublish(ContentId contentId, ContentResult contentResult) throws EscenicException {

	}

	public void processUnpublish(ContentId contentId, ContentResult contentResult) throws EscenicException {

	}

	public void process(ContentId contentId, ContentResult contentResult, String action) throws EscenicException {

		if (StringUtils.equalsIgnoreCase(action, EscenicProcessor.UNPUBLISH_ACTION)) {

		}

		Object contentBean = escenicUtils.extractContentBean(contentResult);

		final DamEngagementUtils utils = new DamEngagementUtils(contentManager);
		String existingEscenicLocation = null;
		try {
			existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
		} catch (CMException e) {
			throw new EscenicException("Failed to extract content location from engagement: " + e);
		}
		boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);

		if (contentBean != null) {
			if (contentBean instanceof OneArticleBean) {
				log.debug("Processing article to escenic, onecms id: " + IdUtil.toIdString(contentId));
				OneArticleBean article = (OneArticleBean) contentBean;

				String sectionId = extractSectionId(contentResult);

				//attempt to geenerate existing entry if location already exists
				Entry entry = null;
				if (isUpdate) {
					log.debug("Article exists in escenic, attempting to retrieve existing entry from location: " + existingEscenicLocation);

					entry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation);

				}

				List<EscenicContent> escenicContentList = null;
				try {
					escenicContentList = EscenicSmartEmbedProcessor.getInstance().process(contentResult, contentManager, utils, cmServer, article, entry, sectionId);
				} catch (CMException | IOException | URISyntaxException e) {
					throw new RuntimeException("An error occurred while processing embedded content: " + e);
				}

				log.info("Extracted a total of: " + escenicContentList.size() + " inline body embeds to be processed to escenic");

				EscenicContent topElement = null;
				topElement = EscenicArticleProcessor.getInstance().processTopElement(contentResult, contentManager, utils, cmServer, article, entry, escenicContentList, sectionId);

				if (topElement != null) {
					escenicContentList.add(topElement);
				}
				//TODO update article once - wait till the engagement is added
				ContentResult updated = updateArticleBodyWithOnecmsIds(escenicContentList, contentResult);
				if (updated != null) {
					contentResult = updated;
					Object updatedBean = escenicUtils.extractContentBean(updated);
					article = (OneArticleBean) updatedBean;
				}

				String result = EscenicArticleProcessor.getInstance().process(entry, article, contentResult, existingEscenicLocation, escenicContentList);
				//TODO config url below will consist of static part +  variable part

				CloseableHttpResponse response = null;
				if (isUpdate) {
					//the url for update is literally the location -> we should use the engagement here...?
					response = escenicUtils.sendUpdatedContentToEscenic(existingEscenicLocation, result);
				} else {
					response = escenicUtils.sendNewContentToEscenic(result, sectionId);
				}

				evaluateResponse(contentId, existingEscenicLocation, extractIdFromLocation(existingEscenicLocation),true, response, utils, contentResult);

			} else if (contentBean instanceof DamCollectionAspectBean) {
				log.debug("Processing gallery to escenic, onecms id: " + IdUtil.toIdString(contentId));
				DamCollectionAspectBean gallery = (DamCollectionAspectBean) contentBean;
				String sectionId = extractSectionId(contentResult);
				EscenicGalleryProcessor.getInstance().processGallery(contentId, null, utils, sectionId);
			} else {
				log.warn("Unable to process content id: " + IdUtil.toIdString(contentId) + " to escenic - due to its content type");

			}
		}
	}



	private String extractSectionId(ContentResult cr) {


		if (StringUtils.isBlank(EscenicContentProcessor.getInstance().escenicConfig.getWebSectionDimension())) {
			throw new RuntimeException("Web section dimension not specified in the configuration. Unable to proceed");
		}

		//TODO for now we'll always return a test value;
		return "2";

//		if (cr != null && cr.getStatus().isSuccess()) {
//			MetadataInfo metadataInfo = (MetadataInfo) cr.getContent().getAspectData(MetadataInfo.ASPECT_NAME);
//			if (metadataInfo != null) {
//				Metadata metadata = metadataInfo.getMetadata();
//				if (metadata != null) {
//					Dimension dim = metadata.getDimensionById(escenicConfig.getWebSectionDimension());
//					if (dim != null) {
//						List<Entity> entites =  dim.getEntities();
//						//TODO
////						"dimensions": [
////						{
////							"name": "Web Section",
////							"id": "department.categorydimension.inm",
////							"entities": [
////							{
////								"name": "Belfast Telegraph",
////								"id": "Belfast Telegraph",
////								"attributes": [],
////								"entities": [
////								{
////									"name": "Sunday Life",
////									"id": "Belfast Telegraph.Sunday Life",
////									"attributes": [],
////									"entities": [
////									{
////										"name": "Bar Person Of The Year Awards",
////										"id": "Belfast Telegraph.Sunday Life.Bar Person Of The Year Awards",
////										"attributes": [],
////										"entities": [],
////										"childrenOmitted": false,
////										"localizations": {}
////									}
////                      ],
////									"childrenOmitted": false,
////									"localizations": {}
////								}
////                  ],
////								"childrenOmitted": false,
////								"localizations": {}
////							}
////              ],
////							"enumerable": true,
////							"localizations": {}
////						},
//
//
//					}
//
//				} else {
//					throw new RuntimeException("Failed to extract section id due to failure reading metadata");
//				}
//
//			}
//

//		}


	}



	protected EngagementDesc evaluateResponse(ContentId contentId, String existingEscenicLocation, String existingEscenicId,
											  boolean updateWebAttribute, CloseableHttpResponse response, DamEngagementUtils utils, ContentResult cr) throws EscenicException {
		if (response != null) {
			String escenicId = null;
			String escenicLocation = null;
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {

				try {
					escenicLocation = retrieveEscenicLocation(response);
				} catch (IOException | FailedToExtractLocationException e) {
					e.printStackTrace();
				}
				escenicId = extractIdFromLocation(escenicLocation);
				if (StringUtils.isBlank(escenicId)) {
					throw new RuntimeException("Extracted escenic id is blank for location: " + escenicLocation);
				}

				if (updateWebAttribute) {
					try {
						updateWebAttribute(contentId, escenicId);
					} catch (ContentModifiedException e) {
						throw new RuntimeException("An error occurred while attempting to update the web attribute: " + e);
					}
				}

				final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
				processEngagement(contentId, engagement, null, utils, cr);
				return engagement;

			} else if (statusCode == HttpStatus.SC_NO_CONTENT) {
				//ensure the status is online
				if (updateWebAttribute) {
					if (StringUtils.isNotBlank(existingEscenicId)) {
						try {
							updateWebAttribute(contentId, existingEscenicId);
						} catch (ContentModifiedException e) {
							throw new RuntimeException("An error occurred while attempting to update the web attribute: " + e);
						}
					} else {
						log.warn("Unable to update the web attribute due to lack of existing escenic id");
					}
				}

				if (StringUtils.isNotBlank(existingEscenicLocation) && StringUtils.isNotBlank(existingEscenicId)) {
					final EngagementDesc engagement = createEngagementObject(existingEscenicId, existingEscenicLocation, getCurrentCaller());
					processEngagement(contentId, engagement, existingEscenicLocation, utils, cr);
				}

			} else if (statusCode == HttpStatus.SC_CONFLICT) {
				//todo needed?
				//todo what other codes we need to handle?

				throw new EscenicResponseException("An error occurred while attempting to update content: " + statusCode);

				// we'll probably just fail here and ask the user to retry.

			}
		}
		return null;
	}

	private ContentResult<Object> updateArticleBodyWithOnecmsIds(List<EscenicContent> escenicContentList, ContentResult<Object> cr) {
		if (cr.getStatus().isSuccess()) {
			Content<Object> articleBeanContent = cr.getContent();
			if (articleBeanContent != null) {
				OneArticleBean article = (OneArticleBean) articleBeanContent.getContentData();
				if (article != null) {

					if (article.getBody() != null) {
						article.getBody().setText(EscenicSocialEmbedProcessor.getInstance().addOnecmsIdToSocialEmbeds(article.getBody().getText(), escenicContentList));
					}


					ContentWrite<CustomArticleBean> content  = new ContentWriteBuilder<CustomArticleBean>()
						.type(CustomArticleBean.ASPECT_NAME)
						.mainAspectData((CustomArticleBean)article)
						.origin(cr.getContentId())
						.buildUpdate();

					if (content != null) {
						try {
							return contentManager.update(cr.getContent().getId().getContentId(), content, SubjectUtil.fromCaller(getCurrentCaller()));
						} catch (ContentModifiedException e) {
							e.printStackTrace();
						}
					} else {
						throw new RuntimeException("Failed to update article with onecms ids for social embeds");
					}
				}
			}
		}
		return null;

	}

	protected String extractIdFromLocation(String escenicLocation) {
		String id = null;
		if (StringUtils.isNotEmpty(escenicLocation)) {
			id = escenicLocation.substring(escenicLocation.lastIndexOf('/') + 1);
//			http://inm-test-editorial.inm.lan:8081/webservice/escenic/content/250358548
		}

		return id;
	}

	protected String retrieveEscenicLocation(CloseableHttpResponse result) throws IOException, FailedToExtractLocationException {
		if (result != null && result.getEntity() != null) {
			System.out.println(EntityUtils.toString(result.getEntity()));

			Header[] headers = result.getAllHeaders();
			for (int i = 0; i < headers.length; i++) {
				Header header = headers[i];
				System.out.println(header);
				System.out.println("Header: " + i + " = " + header);
				if (StringUtils.endsWithIgnoreCase(header.getName(), "Location")) {
					//todo store the value of the header in the engagement ?
					return	header.getValue();
				}
			}

		} else {
			throw new FailedToExtractLocationException("Failed to extract escenic location from the response");

		}
		return null;
	}

	protected void processEngagement(ContentId contentId, EngagementDesc engagement, String existingEscenicLocation, DamEngagementUtils utils, ContentResult cr) {

		//TODO CHECK do we really care about updates?? won't it always point to the same url?

		Preconditions.checkNotNull(contentId, "contentId cannot be null");
		Preconditions.checkNotNull(engagement, "engagement cannot be null");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(engagement.getAppPk()), "the engagement appPk cannot be null or empty");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(engagement.getUserName()), "the engagement username cannot be null or empty");
		ContentVersionId latestVersion = contentManager.resolve(contentId, SubjectUtil.fromCaller(getCurrentCaller()));

		if (StringUtils.isNotEmpty(existingEscenicLocation)) {
			try {
				ContentWrite<Object> cw  = utils.getUpdateEngagement(latestVersion, Object.class, engagement);

				if (cr == null && cw != null) {
					contentManager.update(contentId, cw,SubjectUtil.fromCaller(getCurrentCaller()));
					return;
				}

				Collection<Aspect> aspects = new ArrayList();
				if (cr != null && cr.getContent() != null) {
					Collection<Aspect> contentResultAspects = cr.getContent().getAspects();
					for (Aspect aspect : contentResultAspects) {
						if (!StringUtils.equalsIgnoreCase(aspect.getName(), ImageEditInfoAspectBean.ASPECT_NAME)) {
							aspects.add(aspect);
						}
					}
				}

				ImageEditInfoAspectBean imageEditInfoAspectBean = (ImageEditInfoAspectBean) cw.getAspect(ImageEditInfoAspectBean.ASPECT_NAME);
				if (imageEditInfoAspectBean != null) {
					aspects.add(new Aspect(ImageEditInfoAspectBean.ASPECT_NAME, imageEditInfoAspectBean));
				}

				EngagementAspect eng = (EngagementAspect) cw.getAspect(EngagementAspect.ASPECT_NAME);
				if (eng != null) {
					aspects.add(new Aspect(EngagementAspect.ASPECT_NAME, eng));
				}

				ContentWrite cwb = ContentWriteBuilder.from(cw)
					.origin(latestVersion)
					.type(cr.getContent().getContentDataType())
					.mainAspect(cr.getContent().getContentAspect())
					.aspects(aspects)
					.buildUpdate();


				contentManager.update(contentId, cwb, SubjectUtil.fromCaller(getCurrentCaller()));

			} catch (CMException | ContentModifiedException e) {
				throw new RuntimeException("An error occurred while processing an engagement");
			}
		} else {
			try {
				ContentWrite cw = utils.getAddEngagement(latestVersion, Object.class, engagement);

				if (cr == null && cw != null) {
					contentManager.update(contentId, cw,SubjectUtil.fromCaller(getCurrentCaller()));
					return;
				}

				Collection<Aspect> aspects = new ArrayList();

				if (cr != null && cr.getContent() != null) {
					Collection<Aspect> contentResultAspects = cr.getContent().getAspects();
					for (Aspect aspect : contentResultAspects) {
						if (!StringUtils.equalsIgnoreCase(aspect.getName(), ImageEditInfoAspectBean.ASPECT_NAME)) {
							aspects.add(aspect);
						}
					}
				}
				EngagementAspect eng = (EngagementAspect) cw.getAspect(EngagementAspect.ASPECT_NAME);
				ImageEditInfoAspectBean imageEditInfoAspectBean = (ImageEditInfoAspectBean) cw.getAspect(ImageEditInfoAspectBean.ASPECT_NAME);
				if (imageEditInfoAspectBean != null) {
					aspects.add(new Aspect(ImageEditInfoAspectBean.ASPECT_NAME, imageEditInfoAspectBean));
				}
				if (eng != null) {
					aspects.add(new Aspect(EngagementAspect.ASPECT_NAME, eng));
				}
				ContentWrite cwb = ContentWriteBuilder.from(cw)
					.origin(latestVersion)
					.type(cr.getContent().getContentDataType())
					.mainAspect(cr.getContent().getContentAspect())
					.aspects(aspects)
					.buildUpdate();

				contentManager.update(contentId, cwb, SubjectUtil.fromCaller(getCurrentCaller()));
			} catch (CMException | ContentModifiedException e) {
				throw new RuntimeException("An error occurred while processing an engagement");
			}
		}

	}

	protected EngagementDesc createEngagementObject(String escenicId, String escenicLocation, Caller caller) {
		if (escenicId == null) {
			escenicId = "";
		}

		final EngagementDesc engagement = new EngagementDesc();
		engagement.setAppType(ESCENIC_APPTYPE);
		engagement.setAppPk(escenicId);
		engagement.setUserName("sysadmin");
		engagement.getAttributes().add(createElement("link", escenicId));
		engagement.getAttributes().add(createElement("location", escenicLocation != null ? escenicLocation : ""));
		return engagement;
	}

	private EngagementElement createElement(final String name, final String value) {
		final EngagementElement element = new EngagementElement();
		element.setName(name);
		element.setValue(value);
		return element;
	}

	protected String getEscenicIdFromEngagement(final DamEngagementUtils utils, final ContentId contentId) throws CMException {
		final EngagementAspect engAspect = utils.getEngagement(contentId);
		if (engAspect != null) {
			final EngagementDesc engagement = Iterables.getFirst(
				Iterables.filter(engAspect.getEngagementList(), new Predicate<EngagementDesc>() {
					@Override
					public boolean apply(@Nullable final EngagementDesc engagementDesc) {
						return (engagementDesc != null) && com.polopoly.common.lang.StringUtil.equals(engagementDesc.getAppType(), ESCENIC_APPTYPE);
					}
				}), null);
			if (engagement != null) {
				for (EngagementElement e : engagement.getAttributes()){
					if (StringUtils.equalsIgnoreCase(e.getName(), "location")) {
						return e.getValue();
					}
				}
			}
		}
		return null;
	}


	protected Caller getCurrentCaller() {
		return Optional
			.ofNullable(latestCaller)
			.orElse(new Caller(new UserId("98")));
	}


	//TODO EMBARGO STUFF

	public EmbargoState getEmbargoState(CustomArticleBean content) {

		EmbargoState state = EmbargoState.NOEMBARGO;
		final CustomArticleBean contentState = content;
		if (contentState != null) {
			final TimeState timeState = contentState.getTimeState();
			if (timeState != null) {
				final Calendar now = Calendar.getInstance();

				final long onTime = timeState.getOntime();
				final long offTime = timeState.getOfftime();

				if (offTime > 0) {
					final Calendar off = Calendar.getInstance();
					off.setTimeInMillis(offTime);
					if (off.before(now)) {
						return EmbargoState.TIMEOFF_PASSED;
					}
					state = EmbargoState.EMBARGOED;
				}
				if (onTime > 0) {
					final Calendar on = Calendar.getInstance();
					on.setTimeInMillis(onTime);
					if (on.before(now)) {
						return EmbargoState.TIMEON_PASSED;
					}
					state = EmbargoState.EMBARGOED;
				}
			}
		}
		return state;
	}

	public WFContentStatusAspectBean updateAttributes(CustomArticleBean bean, WFContentStatusAspectBean wfStatus){

		if(wfStatus != null &&  wfStatus.getStatus() != null){
			EmbargoState embargoState = getEmbargoState(bean);
			//clear attributes
			wfStatus.getStatus().getAttributes().clear();
			switch (embargoState) {
				case TIMEOFF_PASSED:
					wfStatus.getStatus().getAttributes().add(STATUS_ATTR_UNPUBLISHED);
					break;
				case EMBARGOED:
					wfStatus.getStatus().getAttributes().add(STATUS_ATTR_EMBARGO);
					break;
				case TIMEON_PASSED:
				default:
					wfStatus.getStatus().getAttributes().add(STATUS_ATTR_ONLINE);

			}
		}

		return wfStatus;
	}

	private void updateWebAttribute(ContentId contentId, String escenicId) throws ContentModifiedException {//change code here to load content again?
		ContentVersionId latestVersion = this.contentManager.resolve(contentId, Subject.NOBODY_CALLER);

		ContentResult<OneContentBean> cr = contentManager.get(latestVersion, null, OneContentBean.class, null, Subject.NOBODY_CALLER);
		if (cr != null && cr.getStatus().isSuccess()) {
			if (cr.getContent() != null) {
				Content content = cr.getContent();
				OneArticleBean articleBean = (OneArticleBean) cr.getContent().getContentData();
				WFContentStatusAspectBean status = (WFContentStatusAspectBean) content.getAspectData(WFContentStatusAspectBean.ASPECT_NAME);
				if (status != null && articleBean != null) {
//					WFStatusBean onlineStatus = new WebStatusUtils(contentManager).getStatusById("online");
					WFStatusBean onlineStatus = status.getStatus();
					if (onlineStatus != null) {
						status.setStatus(onlineStatus);
						status = updateAttributes((CustomArticleBean)articleBean, status);

						if(status != null && status.getStatus() != null && !status.getStatus().getAttributes().isEmpty()){

//							if(StringUtils.equals(status.getStatus().getAttributes().get(0), STATUS_ATTR_UNPUBLISHED)){
//
//								//if we have an unpublished attribute, we need to change the web status....
//								WFStatusBean newStatus = new WebStatusUtils(contentManager).getStatusById("unpublished");
//								status.setStatus(newStatus);
//							}
						}

						//TODO is this needed?
						final ContentWrite<Object> cw = new ContentWriteBuilder<Object>()
							.origin(content.getId())
							.aspects(content.getAspects())
							.mainAspectData(content.getContentData())
							.aspect(PublishingBean.ASPECT_NAME, new PublishingBean(
								escenicId,
								content.getContentDataType(),
								"system").action(PublishingBean.PUBLISH_ACTION)).buildUpdate();
						contentManager.update(content.getId().getContentId(), cw, Subject.NOBODY_CALLER);

					}
				}
			}
		}
	}

	private enum EmbargoState {
		TIMEOFF_PASSED,
		TIMEON_PASSED,
		EMBARGOED,
		NOEMBARGO
	}

}
