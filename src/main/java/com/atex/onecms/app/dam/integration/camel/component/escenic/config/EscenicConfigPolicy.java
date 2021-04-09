package com.atex.onecms.app.dam.integration.camel.component.escenic.config;

import com.atex.onecms.app.dam.integration.camel.component.escenic.EscenicConfig;
import com.polopoly.cm.app.policy.SingleValuePolicy;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.ContentPolicy;
import com.polopoly.model.DescribesModelType;
import com.polopoly.cm.ExternalContentId;
import com.polopoly.cm.client.ContentRead;
import com.polopoly.siteengine.field.properties.ComponentMapProvider;

import java.util.Collections;
import java.util.Map;


@DescribesModelType
public class EscenicConfigPolicy extends ContentPolicy{

	public static final String CONFIG_EXTERNAL_ID = "plugins.com.atex.plugins.camel-post-to-escenic.EscenicConfigHome";
	public static final String DEFAULT_CONFIG_EXTERNAL_ID = "plugins.com.atex.plugins.camel-post-to-escenic.EscenicConfigHome.default";


	public static String useDefault = "false";

	private static final String DEFAULT_VALUE = "useDefault";
    protected static final String USERNAME = "username";
    protected static final String PASSWORD = "password";
    protected static final String API_URL = "apiUrl";
    protected static final String BINARY_URL = "binaryUrl";
    protected static final String MODEL_URL = "modelUrl";
    protected static final String WEB_SECTION_DIMENSION = "webSectionDimension";
    protected static final String MAX_IMAGE_WIDTH = "maxImageWidth";
    protected static final String MAX_IMAGE_HEIGHT = "maxImageHeight";
    protected static final String CROPS_MAPPING = "cropsMapping";
    protected static final String SECTION_LIST_USERNAME = "sectionListUsername";
    protected static final String SECTION_LIST_PASSWORD = "sectionListPassword";
    protected static final String SECTION_LIST_URL = "sectionListUrl";
	protected static final String CONTENT_URL = "contentUrl";
	protected static final String ESCENIC_TOP_LEVEL_SEARCH_URL = "escenicTopLevelSearchUrl";
	protected static final String TAG_LIST_URL = "tagListUrl";
	protected static final String TAG_DIMENSIONS = "tagDimensions";
	protected static final String TAG_RELEVANCE = "tagRelevance";

    @Override
    protected void initSelf() {
        super.initSelf();
    }

	@Override
	public void preCommitSelf() throws CMException {
		super.preCommitSelf();
		useDefault = getUseDefault();
		if (useDefault == "true") {
			final String contentIdToUse = DEFAULT_CONFIG_EXTERNAL_ID;
			final ContentRead content = this.getCMServer().getContent(new ExternalContentId(contentIdToUse));
			String[] componentList = content.getComponentGroupNames();
			for (String componentName : componentList) {
				if (componentName != "useDefault") {
					String[] propertyNames = content.getComponentNames(componentName);
					for (String propertyName : propertyNames) {
						String propertyValue = content.getComponent(componentName, propertyName);
						this.setComponent(componentName, propertyName, propertyValue);
					}
				}
			}
		}
	}

	public String getUseDefault() throws CMException {
		final SingleValuePolicy checkBoxPolicy = (SingleValuePolicy) getChildPolicy(DEFAULT_VALUE);
		final String useDefaultData = checkBoxPolicy.getValue();
		return useDefaultData;
	}

    public String getUsername() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(USERNAME)).getValue();
    }

    public String getPassword() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(PASSWORD)).getValue();
    }

    public String getApiUrl() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(API_URL)).getValue();
    }

    public String getBinaryUrl() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(BINARY_URL)).getValue();
    }

    public String getModelUrl() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(MODEL_URL)).getValue();
    }

    public String getWebSectionDimension() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(WEB_SECTION_DIMENSION)).getValue();
    }

    public String getImageMaxWidth() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(MAX_IMAGE_WIDTH)).getValue();
    }

    public String getImageMaxHeigth() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(MAX_IMAGE_HEIGHT)).getValue();
    }

    public String getCropsMapping() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(CROPS_MAPPING)).getValue();
    }

    public String getSectionListUsername() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(SECTION_LIST_USERNAME)).getValue();
    }

    public String getSectionListPassword() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(SECTION_LIST_PASSWORD)).getValue();
    }

    public String getSectionListUrl() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(SECTION_LIST_URL)).getValue();
    }

	public String getContentUrl() throws CMException {
		return ((SingleValuePolicy) getChildPolicy(CONTENT_URL)).getValue();
	}

	public String getEscenicTopLevelSearchUrl() throws CMException {
		return ((SingleValuePolicy) getChildPolicy(ESCENIC_TOP_LEVEL_SEARCH_URL)).getValue();
	}

	public String getTagListUrl() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(TAG_LIST_URL)).getValue();
    }

    public Map<String, String> getTagDimensions() throws CMException {
        ComponentMapProvider componentMapPolicy = (ComponentMapProvider) getChildPolicy(TAG_DIMENSIONS);
        return Collections.unmodifiableMap(componentMapPolicy.getComponentMap());
    }

    public String getTagRelevance() throws CMException {
        return ((SingleValuePolicy) getChildPolicy(TAG_RELEVANCE)).getValue();
    }

    public EscenicConfig getConfig() throws CMException {

        EscenicConfig bean = new EscenicConfig();
        bean.setUsername(getUsername());
        bean.setPassword(getPassword());
        bean.setModelUrl(getModelUrl());
        bean.setWebSectionDimension(getWebSectionDimension());
        bean.setApiUrl(getApiUrl());
        bean.setBinaryUrl(getBinaryUrl());
        bean.setMaxImgHeight(getImageMaxHeigth());
        bean.setMaxImgWidth(getImageMaxWidth());
        bean.setCropsMapping(getCropsMapping());
        bean.setSectionListUsername(getSectionListUsername());
        bean.setSectionListPassword(getSectionListPassword());
        bean.setSectionListUrl(getSectionListUrl());
        bean.setContentUrl(getContentUrl());
        bean.setEscenicTopLevelSearchUrl(getEscenicTopLevelSearchUrl());
        bean.setTagListUrl(getTagListUrl());
        bean.setTagDimensions(getTagDimensions());
        bean.setTagRelevance(getTagRelevance());
        return bean;
    }
}
