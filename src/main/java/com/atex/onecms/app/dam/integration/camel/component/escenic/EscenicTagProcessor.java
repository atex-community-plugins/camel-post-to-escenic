package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Field;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Origin;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Payload;
import com.atex.onecms.content.*;
import com.atex.onecms.content.aspects.Aspect;
import com.atex.onecms.content.metadata.MetadataInfo;
import com.polopoly.metadata.Attribute;
import com.polopoly.metadata.Dimension;
import com.polopoly.metadata.Entity;
import com.polopoly.metadata.Metadata;
import org.apache.commons.lang.ObjectUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EscenicTagProcessor extends EscenicContentProcessor {

    private static EscenicTagProcessor instance;
    protected static final Logger LOGGER = Logger.getLogger(EscenicTagProcessor.class.getName());
    private static final Subject SYSTEM_SUBJECT = new Subject("98", (String)null);
    private static final String ESCENIC_ID_REFERENCE = "escenicID";


    public EscenicTagProcessor(EscenicUtils escenicUtils) {
        super(escenicUtils);
    }

    public synchronized static EscenicTagProcessor getInstance() {
        if (instance == null) {
            throw new RuntimeException("EscenicTagProcessor not initialized");
        }
        return instance;
    }

    public synchronized static void initInstance(EscenicUtils escenicUtils) {
        if (instance == null) {
            instance = new EscenicTagProcessor(escenicUtils);
        }
    }

    @Nullable
    protected com.atex.onecms.app.dam.integration.camel.component.escenic.model.List process(ContentId contentId) throws EscenicException {
        LOGGER.fine("Processing Tags for content id: " + IdUtil.toIdString(contentId));
        com.atex.onecms.app.dam.integration.camel.component.escenic.model.List modelList = new com.atex.onecms.app.dam.integration.camel.component.escenic.model.List();
        try {
            modelList.setPayloadList(processTags(contentId));
        } catch (Exception e) {
            throw new EscenicException("Failed to process tags for content id: " + IdUtil.toIdString(contentId), e);
        }
        return modelList;
    }

    private Map<String, String> getDimensionMap () {
        return escenicConfig.getTagDimensions();
    }



    private List<Payload> processTags(ContentId contentId) {
        getDimensionMap();
        ContentVersionId contentVersionId = contentManager.resolve(contentId, Subject.NOBODY_CALLER);
        ContentResult<Object> cr = contentManager.get(contentVersionId, null, Object.class, null, SYSTEM_SUBJECT);
        final Aspect<MetadataInfo> metadataInfo = cr.getContent().getAspect(MetadataInfo.ASPECT_NAME);
        List<Payload> payloadList = new ArrayList<>();
        if (metadataInfo == null) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve metadata aspect for : " + contentId);
            return payloadList;
        } else {
            MetadataInfo metadata = metadataInfo.getData();
            if(metadata == null) {
                LOGGER.log(Level.SEVERE, "Metadata is null for : " + contentId);
                return payloadList;
            } else {
                return buildTags(metadata, payloadList);
            }
        }


    }

    private List<Payload> buildTags(MetadataInfo metadata, List<Payload> payloadList) {
        Map<String, String> dimensionMap = getDimensionMap();
        if(dimensionMap != null && !dimensionMap.isEmpty()) {
            for (String key: dimensionMap.keySet()) {
                if (key != null) {
                    Dimension dimension = metadata.getMetadata().getDimensionById(key);
                    if(dimension != null) {
                        List<Entity> entities = dimension.getEntities();
                        if (!entities.isEmpty()){
                            for (Entity entity : entities) {
                                payloadList.add(buildPayload(dimension, entity));
                            }
                        }
                    }
                } else {
                    LOGGER.log(Level.SEVERE, "Key in dimension mapping is null ");
                }
            }
        }
        return payloadList;
    }


    private Payload buildPayload(Dimension dimension, Entity entity) {
        Payload payload = new Payload();
        List<Field> fields  = new ArrayList();
        Field originField = escenicUtils.createField("tag", entity.getName(), null, null);
        originField.setOrigin(generateTagOrigin(dimension, entity));
        fields.add(originField);
        fields.add(escenicUtils.createField("relevance", escenicConfig.getTagRelevance(), null, null));
        payload.setField(fields);
        return payload;
    }

    private String generateValueField(Entity entity) {
        return entity.getName();
    }

    private Origin generateTagOrigin(Dimension dimension, Entity entity) {
        Origin tagOrigin = new Origin();
        String href = escenicConfig.getTagListUrl() +  getDimensionReference(dimension.getId())+ ":" + getEscenicTagIdFromEntity(entity);
        tagOrigin.setHref(href);
        return tagOrigin;
    };

    private String getDimensionReference(String dimensionId) {
        Map<String, String> dimensionMap = getDimensionMap();
        if (dimensionMap != null) return dimensionMap.get(dimensionId);
        return "";
    }

    private String getEscenicTagIdFromEntity(Entity entity) {
        List<Attribute> attributeList = entity.getAttributes();
        String escenicId = null;
        if(attributeList.isEmpty()) {
            escenicId = entity.getName();
        } else {
            for(Attribute attribute : attributeList) {
                if (attribute.getName().equals(escenicConfig.getTagEscenicReferenceAttribute())){
                    escenicId = attribute.getValue();
                } else {
                    escenicId = entity.getName();
                }
            }
        }
        return escenicId;
    }


}
