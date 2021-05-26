package com.atex.onecms.app.dam.integration.camel.component.escenic;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.atex.onecms.app.dam.engagement.EngagementAspect;
import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.engagement.EngagementElement;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicResponseException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToExtractLocationException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToProcessSectionIdException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Entry;
import com.atex.onecms.app.dam.standard.aspects.DamCollectionAspectBean;
import com.atex.onecms.app.dam.standard.aspects.OneArticleBean;
import com.atex.onecms.app.dam.standard.aspects.OneContentBean;
import com.atex.onecms.app.dam.types.TimeState;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.app.dam.workflow.WFContentStatusAspectBean;
import com.atex.onecms.app.dam.workflow.WFStatusBean;
import com.atex.onecms.app.dam.workflow.WebContentStatusAspectBean;

import com.atex.onecms.content.Content;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.SubjectUtil;
import com.atex.onecms.content.aspects.Aspect;
import com.atex.onecms.content.repository.ContentModifiedException;
import com.atex.onecms.image.ImageEditInfoAspectBean;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.PolicyCMServer;

import com.polopoly.siteengine.structure.SitePolicy;
import com.polopoly.user.server.Caller;
import com.polopoly.user.server.UserId;

import org.apache.commons.lang.StringUtils;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import org.json.JSONException;

public class EscenicContentProcessor {

	protected EscenicConfig escenicConfig;
	protected Caller latestCaller = null;
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

	public EscenicContentProcessor(EscenicUtils escenicUtils) {
		this.escenicUtils = escenicUtils;
		this.contentManager = escenicUtils.getContentManager();
		this.cmServer = escenicUtils.getCmServer();
		this.escenicConfig = escenicUtils.getEscenicConfig();
	}

	public static synchronized EscenicContentProcessor getInstance() {
		if (instance == null) {
			throw new RuntimeException("EscenicContentProcessor not initialized");
		}
		return instance;
	}

	public static synchronized void initInstance(EscenicUtils escenicUtils) {
		if (instance == null) {
			instance = new EscenicContentProcessor(escenicUtils);
		}
	}

	public void setEscenicUtils(EscenicUtils escenicUtils) {
		this.escenicUtils = escenicUtils;
	}

	public void setEscenicConfig(EscenicConfig escenicConfig) {
		this.escenicConfig = escenicConfig;
	}

	public void process(ContentId contentId, ContentResult contentResult, String action) throws EscenicException, JSONException, CMException {
		LOGGER.info("Processing content id: " + IdUtil.toIdString(contentId));
		Object contentBean = escenicUtils.extractContentBean(contentResult);
		if (contentBean == null) {
			LOGGER.warning("extracted content bean was null");
			throw new RuntimeException("Unable to process item: " + IdUtil.toIdString(contentId) + " due to failure to extract content bean");
		}

		String existingEscenicLocation = null;
		try {
			existingEscenicLocation = getEscenicIdFromEngagement(contentId);
		} catch (CMException e) {
			throw new EscenicException("Failed to extract content location from engagement: " + e);
		}
		boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);

		LOGGER.finest("ContentBean type is : " + contentBean.getClass().getName());
		if (contentBean instanceof OneArticleBean) {
			LOGGER.finest("Processing article to escenic, onecms id: " + IdUtil.toIdString(contentId));

			OneArticleBean article = (OneArticleBean) contentBean;

			Websection websection = extractSectionId(contentResult);

			//attempt to generate existing entry if location already exists
			Entry entry = null;
			if (isUpdate) {
				LOGGER.finest("Article exists in escenic, attempting to retrieve existing entry from location: " + existingEscenicLocation);
				entry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation);

				if (!escenicUtils.isUpdateAllowed(entry)) {
					throw new EscenicException("Editing in Desk is disabled. This item can only be updated in CUE");
				}
			}

			List<EscenicContent> escenicContentList = EscenicArticleProcessor.getInstance().processTopElements(article, websection, action);

			List<EscenicContent> inlineContentList;
			try {
				inlineContentList = EscenicSmartEmbedProcessor.getInstance().process(contentResult, article, escenicContentList, websection, action);
			} catch (IOException | URISyntaxException e) {
				throw new EscenicException("An error occurred while processing embedded content: " + e);
			}

			LOGGER.finest("Extracted a total of: " + inlineContentList.size() + " inline body embeds to be processed to escenic");

			if (inlineContentList != null && !inlineContentList.isEmpty()) {
				escenicContentList.addAll(inlineContentList);
			}

			article = updateArticleBodyWithOnecmsIds(escenicContentList, contentResult);
			String result = EscenicArticleProcessor.getInstance().process(entry, article, escenicContentList, action, websection, contentId, contentResult);
			CloseableHttpResponse response = null;
			if (isUpdate) {
				response = escenicUtils.sendUpdatedContentToEscenic(existingEscenicLocation, result);
			} else {
				response = escenicUtils.sendNewContentToEscenic(result, websection);
			}

			evaluateResponse(contentId, existingEscenicLocation, escenicUtils.extractIdFromLocation(existingEscenicLocation), true, response, contentResult, action);

		} else if (contentBean instanceof DamCollectionAspectBean) {
			LOGGER.finest("Processing gallery to escenic, onecms id: " + IdUtil.toIdString(contentId));
			Websection websection = extractSectionId(contentResult);
			EscenicGalleryProcessor.getInstance().processGallery(contentId, websection, action);
		} else {
			LOGGER.severe("Attempted to send " + contentBean.getClass().getName() + " directly to escenic");
			throw new RuntimeException("Unable to process content id: " + IdUtil.toIdString(contentId) + " to escenic - due to its content type");
		}
	}

	protected Websection extractSectionId(ContentResult cr) throws FailedToProcessSectionIdException {
		if (cr != null && cr.getStatus().isSuccess()) {
			InsertionInfoAspectBean insertionInfoAspectBean = (InsertionInfoAspectBean) cr.getContent().getAspectData(InsertionInfoAspectBean.ASPECT_NAME);
			if (insertionInfoAspectBean != null) {
				ContentId securityParentId = insertionInfoAspectBean.getSecurityParentId();
				if (securityParentId != null) {
					try {
						SitePolicy sitePolicy = (SitePolicy) cmServer.getPolicy(IdUtil.toPolicyContentId(securityParentId));
						if (sitePolicy != null) {
                            return buildWebsection(sitePolicy, securityParentId);
						}
					} catch (CMException e) {
						throw new FailedToProcessSectionIdException("Problem occurred when retrieving escenic section id for site id : " + securityParentId);
					}
				} else {
					throw new FailedToProcessSectionIdException("Failed to find site information. Unable to proceed.");
				}
			}
		}

		throw new FailedToProcessSectionIdException("Unable to retrieve escenic section id for content: " + cr.getContentId());
	}

	protected Websection buildWebsection(SitePolicy sitePolicy, ContentId contentId) throws FailedToProcessSectionIdException, CMException {
		String escenicId = null;
		String publicationName = null;
		escenicId = sitePolicy.getComponent("escenicId", "value");
		publicationName = sitePolicy.getComponent("publicationKey", "value");

		if (escenicId == null || publicationName == null) {
			throw new FailedToProcessSectionIdException("Failed to find site information (site id or publication name). Unable to proceed.");
		}

		return new Websection(escenicId, publicationName, contentId);

	}

    protected EngagementDesc evaluateResponse(ContentId contentId,
                                              String existingEscenicLocation,
                                              String existingEscenicId,
                                              boolean updateWebAttribute,
                                              CloseableHttpResponse response,
                                              ContentResult cr,
                                              String action) throws EscenicException {
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
				escenicId = escenicUtils.extractIdFromLocation(escenicLocation);
				if (StringUtils.isBlank(escenicId)) {
					throw new RuntimeException("Extracted escenic id is blank for location: " + escenicLocation);
				}
				WFContentStatusAspectBean wfContentStatusAspectBean = null;
				WebContentStatusAspectBean webContentStatusAspectBean = null;
				if (updateWebAttribute) {
					try {
						wfContentStatusAspectBean = getUpdatedStatusWithAttribute(cr, action);
						webContentStatusAspectBean = getUpdatedWebStatus(cr, action);
					} catch (ContentModifiedException e) {
						throw new RuntimeException("An error occurred while attempting to update the web attribute: " + e);
					}
				}

				final EngagementDesc engagement = createEngagementObject(escenicId);
				processEngagement(contentId, engagement, null, cr, wfContentStatusAspectBean, webContentStatusAspectBean);
				return engagement;

			} else if (statusCode == HttpStatus.SC_NO_CONTENT) {
				//ensure the status is online
				WFContentStatusAspectBean wfContentStatusAspectBean = null;
				WebContentStatusAspectBean webContentStatusAspectBean = null;
				if (updateWebAttribute) {
					if (StringUtils.isNotBlank(existingEscenicId)) {
						try {
							wfContentStatusAspectBean = getUpdatedStatusWithAttribute(cr, action);
							webContentStatusAspectBean = getUpdatedWebStatus(cr, action);
						} catch (ContentModifiedException e) {
							throw new RuntimeException("An error occurred while attempting to update the web attribute: " + e);
						}
					} else {
						LOGGER.warning("Unable to update the web attribute due to lack of existing escenic id");
					}
				}

				if (StringUtils.isNotBlank(existingEscenicLocation) && StringUtils.isNotBlank(existingEscenicId)) {
					final EngagementDesc engagement = createEngagementObject(existingEscenicId);
					processEngagement(contentId, engagement, existingEscenicLocation, cr, wfContentStatusAspectBean, webContentStatusAspectBean );
				}

			} else {
				throw new EscenicResponseException("An error occurred while attempting to update content: " + response.getStatusLine());

			}
		}
		return null;
	}


	private OneArticleBean updateArticleBodyWithOnecmsIds(List<EscenicContent> escenicContentList, ContentResult<Object> cr) {
		if (cr.getStatus().isSuccess()) {
			Content<Object> articleBeanContent = cr.getContent();
			if (articleBeanContent != null) {
				OneArticleBean article = (OneArticleBean) articleBeanContent.getContentData();
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

	protected String retrieveEscenicLocation(CloseableHttpResponse result) throws IOException, FailedToExtractLocationException {
		if (result != null) {
			try {
				int statusCode = result.getStatusLine().getStatusCode();
				if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
					LOGGER.finest("Returned entity:\n" + EntityUtils.toString(result.getEntity()));
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

    protected void processEngagement(ContentId contentId,
                                     EngagementDesc engagement,
                                     String existingEscenicLocation,
                                     ContentResult cr) {

		processEngagement(contentId, engagement, existingEscenicLocation, cr, null, null);
	}

    protected void processEngagement(ContentId contentId,
                                     EngagementDesc engagement,
                                     String existingEscenicLocation,
                                     ContentResult cr,
                                     WFContentStatusAspectBean wfContentStatusAspectBean,
                                     WebContentStatusAspectBean webContentStatusAspectBean) {

		Preconditions.checkNotNull(contentId, "contentId cannot be null");
		Preconditions.checkNotNull(engagement, "engagement cannot be null");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(engagement.getAppPk()), "the engagement appPk cannot be null or empty");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(engagement.getUserName()), "the engagement username cannot be null or empty");
		ContentVersionId latestVersion = contentManager.resolve(contentId, SubjectUtil.fromCaller(getCurrentCaller()));

		if (StringUtils.isNotEmpty(existingEscenicLocation)) {
			try {
				ContentWrite<Object> cw  = escenicUtils.getEngagementUtils().getUpdateEngagement(latestVersion, Object.class, engagement);
				Collection<Aspect> aspects = getFinalAspects(cr, cw, wfContentStatusAspectBean, webContentStatusAspectBean);

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
				ContentWrite cw = escenicUtils.getEngagementUtils().getAddEngagement(latestVersion, Object.class, engagement);
				Collection<Aspect> aspects = getFinalAspects(cr, cw, wfContentStatusAspectBean, webContentStatusAspectBean);

				ContentWrite cwb = ContentWriteBuilder.from(cw)
					.origin(latestVersion)
					.aspects(aspects)
					.mainAspect(cr.getContent().getContentAspect())
					.buildUpdate();

				contentManager.update(contentId, cwb, SubjectUtil.fromCaller(getCurrentCaller()));
			} catch (CMException | ContentModifiedException e) {
				throw new RuntimeException("An error occurred while processing an engagement");
			}
		}

	}

    private Collection<Aspect> getFinalAspects(ContentResult cr,
                                               ContentWrite cw,
                                               WFContentStatusAspectBean wfContentStatusAspectBean,
                                               WebContentStatusAspectBean webContentStatusAspectBean) {

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

		if (webContentStatusAspectBean != null) {
			aspects.add(new Aspect(webContentStatusAspectBean.ASPECT_NAME, webContentStatusAspectBean));
		}

		return aspects;
	}


	protected EngagementDesc createEngagementObject(String escenicId) {
		if (escenicId == null) {
			escenicId = "";
		}

		final EngagementDesc engagement = new EngagementDesc();
		engagement.setAppType(ESCENIC_APPTYPE);
		engagement.setAppPk(escenicId);
		engagement.setUserName("sysadmin");
		engagement.getAttributes().add(createElement("link", escenicId));
		engagement.getAttributes().add(createElement("location", escenicConfig.getContentUrl() + escenicId));
		return engagement;
	}

	private EngagementElement createElement(final String name, final String value) {
		final EngagementElement element = new EngagementElement();
		element.setName(name);
		element.setValue(value);
		return element;
	}

	protected String getEscenicIdFromEngagement(final ContentId contentId) throws CMException {
		final DamEngagementUtils utils = escenicUtils.getEngagementUtils();
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

	public EmbargoState getEmbargoState(OneArticleBean content) {

		EmbargoState state = EmbargoState.NOEMBARGO;
		final OneArticleBean contentState = content;
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
					OneArticleBean articleBean = (OneArticleBean) bean;
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

	private WebContentStatusAspectBean getUpdatedWebStatus(ContentResult<OneContentBean> cr, String action) throws ContentModifiedException {
		if (cr != null && cr.getStatus().isSuccess()) {
			if (cr.getContent() != null) {
				Content content = cr.getContent();
				OneContentBean oneContentBean = cr.getContent().getContentData();
				WebContentStatusAspectBean statusBean = (WebContentStatusAspectBean) content.getAspectData(WebContentStatusAspectBean.ASPECT_NAME);
				if (statusBean != null && oneContentBean != null) {
					WFStatusBean nextStatus = null;

					if (StringUtils.equalsIgnoreCase(action, EscenicProcessor.UNPUBLISH_ACTION)) {
						 nextStatus = new WebStatusUtils(contentManager).getStatusById("unpublished");
					} else {
						nextStatus = new WebStatusUtils(contentManager).getStatusById("online");
					}

					if (nextStatus != null) {
						statusBean.setStatus(nextStatus);
					} else {
						LOGGER.log(Level.SEVERE, "Unable to retrieve the next web status");
					}

					return statusBean;

				}
			}
		}
		return null;
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
