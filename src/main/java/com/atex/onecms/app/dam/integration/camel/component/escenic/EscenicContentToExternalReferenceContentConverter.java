package com.atex.onecms.app.dam.integration.camel.component.escenic;

import java.util.List;

import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Entry;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Field;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Link;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.polopoly.user.server.Caller;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author jakub
 */
public class EscenicContentToExternalReferenceContentConverter {

	private ContentManager contentManager;
	private Caller caller;
	public EscenicContentToExternalReferenceContentConverter(ContentManager contentManager, Caller caller) {
		this.contentManager = contentManager;
		this.caller = caller;
	}

	public ContentResult process(Entry entry, String escenicId) {
		String type = null;
		String location = null;
		String thumbnailUrl = null;
		if (entry != null) {
			if (entry.getLink() != null) {
				for (Link link : entry.getLink()) {
					if (link != null) {
						if (StringUtils.equalsIgnoreCase(link.getRel(), "http://www.escenic.com/types/relation/summary-model")) {
							type = link.getTitle();
						}
						if (StringUtils.equalsIgnoreCase(link.getRel(), "self")) {
							location = link.getHref();
						}
						if (StringUtils.equalsIgnoreCase(link.getRel(), "thumbnail")) {
							thumbnailUrl = link.getHref();
						}
					}
				}
			}

			return createExternalReference(type, escenicId, location, thumbnailUrl, entry);
		}
		return null;

	}

	private ContentResult createExternalReference(String type, String escenicId, String location, String thumbnailUrl, Entry entry) {
		/*
		ExternalReferenceBean externalReferenceBean = new ExternalReferenceBean();
		externalReferenceBean.setTitle(getFieldValue(entry.getContent().getPayload().getField(), "title"));
		externalReferenceBean.setExternalReferenceContentType(type);
		externalReferenceBean.setExternalReferenceId(escenicId);
		externalReferenceBean.setLocation(location);
		externalReferenceBean.setThumbnailUrl(thumbnailUrl);

		ContentWrite<ExternalReferenceBean> content  = new ContentWriteBuilder<ExternalReferenceBean>()
			.type(ExternalReferenceBean.ASPECT_NAME)
			.mainAspectData(externalReferenceBean)
			.buildCreate();

		if (content != null) {
			ContentResult<ExternalReferenceBean> cr = contentManager.create(content, SubjectUtil.fromCaller(caller));
			return cr;

		} else {
			throw new RuntimeException("Failed to create ExternalReference object");
		}
		*/
		return null;
	}

	private String getFieldValue(List<Field> fields, String fieldName) {
			if (fields != null) {
				for (Field field : fields) {
					if (field != null) {
						if (StringUtils.equalsIgnoreCase(field.getName(), fieldName)) {
							return field.getValue().getValue().get(0).toString();
						}
					}
				}
			}
			return "";
	}

}
