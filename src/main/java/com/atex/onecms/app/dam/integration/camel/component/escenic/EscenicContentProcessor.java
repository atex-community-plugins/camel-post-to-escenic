package com.atex.onecms.app.dam.integration.camel.component.escenic;

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
import com.atex.onecms.content.metadata.MetadataInfo;
import com.atex.onecms.content.repository.ContentModifiedException;
import com.atex.onecms.image.ImageEditInfoAspectBean;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.PolicyCMServer;
import com.polopoly.metadata.Dimension;
import com.polopoly.metadata.Entity;
import com.polopoly.metadata.Metadata;
import com.polopoly.user.server.Caller;
import com.polopoly.user.server.UserId;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EscenicContentProcessor {


	protected EscenicConfig escenicConfig;
	private Caller latestCaller = null;
	private static final Logger LOGGER = Logger.getLogger(EscenicContentProcessor.class.getName());
	protected ContentManager contentManager;
	protected static PolicyCMServer cmServer;
	protected EscenicUtils escenicUtils;

	protected static final String PUBLISHED_STATE = "published";
	protected static final String UNPUBLISHED_STATE = "approved";
	protected static final String PREACTIVE_STATE = "pre-active";
	protected static final String ESCENIC_APPTYPE = "escenic";
	public static final String STATUS_ATTR_EMBARGO = "attr.embargo";
	public static final String STATUS_ATTR_ONLINE = "attr.online";
	public static final String STATUS_ATTR_UNPUBLISHED = "attr.removed";

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

	public void process(ContentId contentId, ContentResult contentResult, String action) throws EscenicException, JSONException {
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
				LOGGER.info("Processing article to escenic, onecms id: " + IdUtil.toIdString(contentId));
				OneArticleBean article = (OneArticleBean) contentBean;

				String sectionId = extractSectionId(contentResult);

				//attempt to geenerate existing entry if location already exists
				Entry entry = null;
				if (isUpdate) {
					LOGGER.info("Article exists in escenic, attempting to retrieve existing entry from location: " + existingEscenicLocation);

					entry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation);

				}

				List<EscenicContent> escenicContentList = null;
				try {
					escenicContentList = EscenicSmartEmbedProcessor.getInstance().process(contentResult, utils, article, entry, sectionId, action);
				} catch (IOException | URISyntaxException e) {
					throw new RuntimeException("An error occurred while processing embedded content: " + e);
				}

				LOGGER.info("Extracted a total of: " + escenicContentList.size() + " inline body embeds to be processed to escenic");

				EscenicContent topElement = EscenicArticleProcessor.getInstance().processTopElement(contentResult, contentManager, utils, cmServer, article, entry, escenicContentList, sectionId, action);

				if (topElement != null) {
					escenicContentList.add(topElement);
				}

				article = updateArticleBodyWithOnecmsIds(escenicContentList, contentResult);
				String result = EscenicArticleProcessor.getInstance().process(entry, article, escenicContentList, action);
				CloseableHttpResponse response = null;
				if (isUpdate) {
					//the url for update is literally the location -> we should use the engagement here...?
					response = escenicUtils.sendUpdatedContentToEscenic(existingEscenicLocation, result);
				} else {
					response = escenicUtils.sendNewContentToEscenic(result, sectionId);
				}

				evaluateResponse(contentId, existingEscenicLocation, extractIdFromLocation(existingEscenicLocation),true, response, utils, contentResult, action);

			} else if (contentBean instanceof DamCollectionAspectBean) {
				LOGGER.info("Processing gallery to escenic, onecms id: " + IdUtil.toIdString(contentId));
				String sectionId = extractSectionId(contentResult);
				EscenicGalleryProcessor.getInstance().processGallery(contentId, null, utils, sectionId, action);
			} else {
				LOGGER.info("Unable to process content id: " + IdUtil.toIdString(contentId) + " to escenic - due to its content type");
			}
		}
	}



	private String extractSectionId(ContentResult cr) throws FailedToRetrieveEscenicContentException, JSONException {
		if (StringUtils.isBlank(EscenicContentProcessor.getInstance().escenicConfig.getWebSectionDimension())) {
			throw new RuntimeException("Web section dimension not specified in the configuration. Unable to proceed");
		}

//		https://aws-test.independent.ie/?service=SectionList
//		https://aws-test.independent.ie/regionals/argus/?service=SectionList
//		https://aws-test.independent.ie/regionals/sligochampion/?service=SectionList



		String sectionId = null;
		//TODO for now we'll always return a test value;
		if (cr != null && cr.getStatus().isSuccess()) {
			MetadataInfo metadataInfo = (MetadataInfo) cr.getContent().getAspectData(MetadataInfo.ASPECT_NAME);
			if (metadataInfo != null) {
				Metadata metadata = metadataInfo.getMetadata();
				if (metadata != null) {
					Dimension dim = metadata.getDimensionById(escenicConfig.getWebSectionDimension());
					if (dim != null) {
						List<Entity> entites =  dim.getEntities();
						if (entites != null && !entites.isEmpty()) {
							 sectionId = extractEntity(entites.get(0));
						}

					}

				} else {
					throw new RuntimeException("Failed to extract section id due to failure reading metadata");
				}

			}


		}

		String resJson = escenicUtils.retrieveSectionList(escenicConfig.getSectionListUrl()).trim();

		if (StringUtils.isBlank(resJson)) {
			throw new RuntimeException("Failed to retrieve section mapping");
		}

		JsonObject jsonObject = null;
		jsonObject = new Gson().fromJson(resJson,JsonObject.class);

		if (jsonObject != null) {

			JsonArray sections = jsonObject.getAsJsonArray("sections");
			if (sections != null) {
				Iterator<JsonElement> iterator = sections.iterator();
				while(iterator.hasNext()) {
					JsonElement obj = iterator.next();
					if (obj != null) {
						JsonObject json = obj.getAsJsonObject();
						if (json != null) {

							if (json.get("name") != null) {

								String name = json.get("name").getAsString();


								//hack to get it to match..
								name = "irish " + name;
								if (StringUtils.equalsIgnoreCase(name, sectionId)) {
									if (json.get("value") != null) {
										String value = json.get("value").getAsString();
										return value;
									} else {
										throw new RuntimeException("Unable to resolve the ID for web section: " + sectionId);
									}

								}
							}
						}

					}
				}
			}
		}

		throw new RuntimeException("Unable to resolve the ID for web section");

	}

	private String extractEntity(Entity entity) {
		if (entity != null) {

			if (entity.getEntities() != null && entity.getEntities().size() == 0) {
				return entity.getId();
			} else if (entity.getEntities() != null) {
				return extractEntity(entity.getEntities().get(0));
			} else {
				throw new RuntimeException("Failed to get entities: ");
			}
		} else {
			throw new RuntimeException("Unable to extract section id from content");
		}
	}

	protected EngagementDesc evaluateResponse(ContentId contentId, String existingEscenicLocation, String existingEscenicId,
											  boolean updateWebAttribute, CloseableHttpResponse response, DamEngagementUtils utils, ContentResult cr, String action) throws EscenicException {
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
				WFContentStatusAspectBean wfContentStatusAspectBean = null;
				if (updateWebAttribute) {
					try {
						wfContentStatusAspectBean = getUpdatedStatusWithAttribute(cr, action);
					} catch (ContentModifiedException e) {
						throw new RuntimeException("An error occurred while attempting to update the web attribute: " + e);
					}
				}


				final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
				processEngagement(contentId, engagement, null, utils, cr, wfContentStatusAspectBean);
				return engagement;

			} else if (statusCode == HttpStatus.SC_NO_CONTENT) {
				//ensure the status is online
				WFContentStatusAspectBean wfContentStatusAspectBean=null;
				if (updateWebAttribute) {
					if (StringUtils.isNotBlank(existingEscenicId)) {
						try {
							wfContentStatusAspectBean = getUpdatedStatusWithAttribute(cr, action);
						} catch (ContentModifiedException e) {
							throw new RuntimeException("An error occurred while attempting to update the web attribute: " + e);
						}
					} else {
						LOGGER.warning("Unable to update the web attribute due to lack of existing escenic id");
					}
				}

				if (StringUtils.isNotBlank(existingEscenicLocation) && StringUtils.isNotBlank(existingEscenicId)) {
					final EngagementDesc engagement = createEngagementObject(existingEscenicId, existingEscenicLocation, getCurrentCaller());
					processEngagement(contentId, engagement, existingEscenicLocation, utils, cr, wfContentStatusAspectBean);
				}

			} else {
				throw new EscenicResponseException("An error occurred while attempting to update content: " + response.getStatusLine());

			}
		}
		return null;
	}


	private CustomArticleBean updateArticleBodyWithOnecmsIds(List<EscenicContent> escenicContentList, ContentResult<Object> cr) {
		if (cr.getStatus().isSuccess()) {
			Content<Object> articleBeanContent = cr.getContent();
			if (articleBeanContent != null) {
				CustomArticleBean article = (CustomArticleBean) articleBeanContent.getContentData();
				if (article != null) {
					if (article.getBody() != null) {
						article.getBody().setText(EscenicSocialEmbedProcessor.getInstance().addOnecmsIdToSocialEmbeds(article.getBody().getText(), escenicContentList));
					}
					return article;
				}
			}
		}
		return null;
	}

	protected String extractIdFromLocation(String escenicLocation) {
		String id = null;
		if (StringUtils.isNotEmpty(escenicLocation)) {
			id = escenicLocation.substring(escenicLocation.lastIndexOf('/') + 1);
		}

		return id;
	}

	protected String retrieveEscenicLocation(CloseableHttpResponse result) throws IOException, FailedToExtractLocationException {
		if (result != null) {
			try {
				int statusCode = result.getStatusLine().getStatusCode();
				if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
					LOGGER.info("Returned entity:\n" + EntityUtils.toString(result.getEntity()));
					Header header = result.getFirstHeader("Location");
					return header.getValue();
				} else {
					throw new EscenicResponseException("Failed to retrieve content from escenic: " + result.getStatusLine());
				}
			} catch(EscenicResponseException e) {
				throw new FailedToExtractLocationException("Failed to extract escenic location from the response" + result.getStatusLine());
			}
		}
		return null;
	}

	protected void processEngagement(ContentId contentId, EngagementDesc engagement, String existingEscenicLocation, DamEngagementUtils utils, ContentResult cr) {
		processEngagement(contentId, engagement, existingEscenicLocation, utils, cr, null);
	}

	protected void processEngagement(ContentId contentId, EngagementDesc engagement, String existingEscenicLocation, DamEngagementUtils utils, ContentResult cr, WFContentStatusAspectBean wfContentStatusAspectBean) {

		Preconditions.checkNotNull(contentId, "contentId cannot be null");
		Preconditions.checkNotNull(engagement, "engagement cannot be null");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(engagement.getAppPk()), "the engagement appPk cannot be null or empty");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(engagement.getUserName()), "the engagement username cannot be null or empty");
		ContentVersionId latestVersion = contentManager.resolve(contentId, SubjectUtil.fromCaller(getCurrentCaller()));

		if (StringUtils.isNotEmpty(existingEscenicLocation)) {
			try {
				ContentWrite<Object> cw  = utils.getUpdateEngagement(latestVersion, Object.class, engagement);



				Collection<Aspect> aspects = getFinalAspects(cr, cw, wfContentStatusAspectBean);

				//TODO .aspect(PublishingBean.ASPECT_NAME, new PublishingBean(
				////								escenicId,
				////								content.getContentDataType(),
				////								"system").action(PublishingBean.PUBLISH_ACTION)).buildUpdate();
				////						contentManager.update(content.getId().getContentId(), cw, Subject.NOBODY_CALLER);



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


				Collection<Aspect> aspects = getFinalAspects(cr, cw, wfContentStatusAspectBean);

				//TODO .aspect(PublishingBean.ASPECT_NAME, new PublishingBean(
				////								escenicId,
				////								content.getContentDataType(),
				////								"system").action(PublishingBean.PUBLISH_ACTION)).buildUpdate();
				////						contentManager.update(content.getId().getContentId(), cw, Subject.NOBODY_CALLER);


				ContentWrite cwb = ContentWriteBuilder.from(cw)
					.origin(latestVersion)
					.aspects(aspects)
					.buildUpdate();

				contentManager.update(contentId, cwb, SubjectUtil.fromCaller(getCurrentCaller()));
			} catch (CMException | ContentModifiedException e) {
				throw new RuntimeException("An error occurred while processing an engagement");
			}
		}

	}

	private Collection<Aspect> getFinalAspects(ContentResult cr, ContentWrite cw, WFContentStatusAspectBean wfContentStatusAspectBean) {
		Collection<Aspect> aspects = new ArrayList();

		if (cr != null && cr.getContent() != null) {
			Collection<Aspect> contentResultAspects = cr.getContent().getAspects();
			for (Aspect aspect : contentResultAspects) {
				if (!StringUtils.equalsIgnoreCase(aspect.getName(), ImageEditInfoAspectBean.ASPECT_NAME)) {
					aspects.add(aspect);
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

		} else if (cw != null){
			aspects = cw.getAspects();
		}


		if (wfContentStatusAspectBean != null) {
			aspects.add(new Aspect(WFContentStatusAspectBean.ASPECT_NAME, wfContentStatusAspectBean));
		}

		return aspects;
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

	public WFContentStatusAspectBean updateAttributes(OneContentBean bean, WFContentStatusAspectBean wfStatus, String action){

		if(wfStatus != null &&  wfStatus.getStatus() != null){
			//clear attributes
			wfStatus.getStatus().getAttributes().clear();
			if (StringUtils.equalsIgnoreCase(action, EscenicProcessor.UNPUBLISH_ACTION)) {
				wfStatus.getStatus().getAttributes().add(STATUS_ATTR_UNPUBLISHED);
			} else {
				if (bean instanceof OneArticleBean) {
					CustomArticleBean articleBean = (CustomArticleBean) bean;
					EmbargoState embargoState = getEmbargoState(articleBean);

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
				} else {
					wfStatus.getStatus().getAttributes().add(STATUS_ATTR_ONLINE);
				}
			}
		}

		return wfStatus;
	}

	private WFContentStatusAspectBean getUpdatedStatusWithAttribute(ContentResult<OneContentBean> cr, String action) throws ContentModifiedException {//change code here to load content again?
		if (cr != null && cr.getStatus().isSuccess()) {
			if (cr.getContent() != null) {
				Content content = cr.getContent();
				OneContentBean oneContentBean = cr.getContent().getContentData();
				WFContentStatusAspectBean statusBean = (WFContentStatusAspectBean) content.getAspectData(WFContentStatusAspectBean.ASPECT_NAME);
				if (statusBean != null && oneContentBean != null) {
					WFStatusBean currentStatus = statusBean.getStatus();
					if (currentStatus != null) {
						statusBean.setStatus(currentStatus);
						statusBean = updateAttributes(oneContentBean, statusBean, action);
						return statusBean;
					}
				}
			}
		}
		return null;
	}

	private enum EmbargoState {
		TIMEOFF_PASSED,
		TIMEON_PASSED,
		EMBARGOED,
		NOEMBARGO
	}

}
