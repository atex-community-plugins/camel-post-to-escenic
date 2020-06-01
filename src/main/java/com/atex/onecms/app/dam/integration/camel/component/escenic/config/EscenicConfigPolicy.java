package com.atex.onecms.app.dam.integration.camel.component.escenic.config;

import com.atex.onecms.app.dam.integration.camel.component.escenic.EscenicConfig;
import com.polopoly.cm.app.policy.SingleValuePolicy;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.ContentPolicy;
import com.polopoly.model.DescribesModelType;

@DescribesModelType
public class EscenicConfigPolicy extends ContentPolicy{

    public static final String CONFIG_EXTERNAL_ID = "plugins.com.atex.plugins.camel-post-to-escenic.Config";

    protected static final String USERNAME = "username";
    protected static final String PASSWORD = "password";
    protected static final String API_URL = "apiUrl";
    protected static final String BINARY_URL = "binaryUrl";
    protected static final String MODEL_URL = "modelUrl";
    protected static final String WEB_SECTION_DIMENSION = "webSectionDimension";
    protected static final String MAX_IMAGE_WIDTH = "maxImageWidth";
    protected static final String MAX_IMAGE_HEIGHT = "maxImageHeight";
    protected static final String CROPS_MAPPING = "cropsMapping";

    @Override
    protected void initSelf() {
        super.initSelf();
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
        return bean;
    }
}
