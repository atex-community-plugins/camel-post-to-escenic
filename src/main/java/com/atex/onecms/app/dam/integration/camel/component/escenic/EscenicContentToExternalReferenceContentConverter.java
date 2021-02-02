package com.atex.onecms.app.dam.integration.camel.component.escenic;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.EscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Entry;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Field;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Link;
import com.atex.onecms.app.dam.standard.aspects.ExternalReferenceBean;

import com.atex.onecms.app.dam.standard.aspects.ExternalReferenceVideoBean;
import com.atex.onecms.app.dam.util.DamUtils;
import com.atex.onecms.app.dam.util.HttpDamUtils;
import com.atex.onecms.app.dam.util.WebServiceResponse;
import com.atex.onecms.content.ContentFileInfo;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.FilesAspectBean;
import com.atex.onecms.content.SubjectUtil;
import com.atex.onecms.content.files.FileInfo;

import com.atex.onecms.image.ImageInfoAspectBean;
import com.atex.onecms.ws.service.AuthenticationUtil;
import com.atex.onecms.ws.service.ErrorResponseException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.polopoly.cm.client.CMException;
import com.polopoly.common.util.FriendlyUrlConverter;
import com.polopoly.user.server.Caller;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import javax.ws.rs.core.UriBuilder;

/**
 *
 * @author jakub
 */
public class EscenicContentToExternalReferenceContentConverter {

	private ContentManager contentManager;

	private static final Logger LOGGER = Logger.getLogger(EscenicContentToExternalReferenceContentConverter.class.getName());

	public EscenicContentToExternalReferenceContentConverter(ContentManager contentManager) {
		this.contentManager = contentManager;
	}

    public ContentResult process(EscenicUtils escenicUtils,
                                 Entry entry, String escenicId,
                                 Caller caller) throws IOException, ErrorResponseException, EscenicException {

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

			return createExternalReference(escenicUtils, type, escenicId, location, thumbnailUrl, entry, caller);
		}
		return null;

	}

    private ContentResult createExternalReference(EscenicUtils escenicUtils,
                                                  String type,
                                                  String escenicId,
                                                  String location,
                                                  String thumbnailUrl,
                                                  Entry entry,
                                                  Caller caller) throws EscenicException {

		ContentWrite content = null;

		if (StringUtils.equalsIgnoreCase(type, "video")) {
			ExternalReferenceVideoBean externalReferenceVideoBean = new ExternalReferenceVideoBean();
			assignProperties(externalReferenceVideoBean, entry, type, escenicId, location, thumbnailUrl);

			try (CloseableHttpResponse response = escenicUtils.getImageThumbnailResponse(externalReferenceVideoBean.getThumbnailUrl())) {
				if (escenicUtils.getResponseStatusCode(response) == HttpStatus.SC_OK) {
					String mimeType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();
					String authToken= AuthenticationUtil.getAuthToken(caller);
					WebServiceResponse webServiceResponse =	uploadToFileService(externalReferenceVideoBean.getName(),response.getEntity().getContent(), mimeType, authToken);
					ContentFileInfo contentFileInfo = buildContentFileInfo(webServiceResponse.getBody(), externalReferenceVideoBean.getName());
					FilesAspectBean filesAspectBean = createFilesAspectBean(contentFileInfo);
					ImageInfoAspectBean imageInfoAspectBean = createImageInfoAspectBean(contentFileInfo);
					content = createContentWrite(externalReferenceVideoBean, filesAspectBean, imageInfoAspectBean);
				} else {
					//if we failed to create the image - fallback and create content without the files & imageInfo aspects.
					LOGGER.log(Level.SEVERE, "Failed to process a thumbnail due to the following response: " + response.getStatusLine());
					content = createContentWrite(externalReferenceVideoBean, null, null);
				}
			} catch (Exception e) {
				//fallback if there's any exception during processing the image
				LOGGER.log(Level.SEVERE, "Failed to process a thumbnail due to : ", e);
				content = createContentWrite(externalReferenceVideoBean, null, null);
			}

		} else {
			ExternalReferenceBean externalReferenceBean = new ExternalReferenceBean();
			assignProperties(externalReferenceBean, entry, type, escenicId, location, thumbnailUrl);
			content = createContentWrite(externalReferenceBean, null, null);
		}

		if (content != null) {
			ContentResult cr = contentManager.create(content, SubjectUtil.fromCaller(caller));
			return cr;

		} else {
			throw new EscenicException("Failed to create ExternalReference object");
		}
	}


	private ContentWrite createContentWrite(Object externalReference, FilesAspectBean filesAspectBean, ImageInfoAspectBean imageInfoAspectBean) throws EscenicException {
		if (externalReference instanceof ExternalReferenceVideoBean) {
			ExternalReferenceVideoBean externalReferenceVideoBean = (ExternalReferenceVideoBean) externalReference;
			ContentWriteBuilder cwb = new ContentWriteBuilder()
				.type(ExternalReferenceVideoBean.ASPECT_NAME)
				.mainAspectData(externalReferenceVideoBean);
			if (filesAspectBean != null) {
				cwb.aspect(FilesAspectBean.ASPECT_NAME, filesAspectBean);
			}

			if (imageInfoAspectBean != null) {
				cwb.aspect(ImageInfoAspectBean.ASPECT_NAME, imageInfoAspectBean);
			}

			return cwb.buildCreate();

		} else if (externalReference instanceof ExternalReferenceBean) {
			ExternalReferenceBean externalReferenceBean = (ExternalReferenceBean) externalReference;
			return new ContentWriteBuilder()
				.type(ExternalReferenceBean.ASPECT_NAME)
				.mainAspectData(externalReferenceBean)
				.buildCreate();
		}

		throw new EscenicException("Attempt to create content write for external reference object failed");
	}

    private void assignProperties(ExternalReferenceBean externalReferenceBean,
                                  Entry entry,
                                  String type,
                                  String escenicId,
                                  String location,
                                  String thumbnailUrl) {

		externalReferenceBean.setTitle(getFieldValue(entry.getContent().getPayload().getField(), "title"));
		externalReferenceBean.setName(getFieldValue(entry.getContent().getPayload().getField(), "title"));
		externalReferenceBean.setExternalReferenceContentType(type);
		externalReferenceBean.setExternalReferenceId(escenicId);
		externalReferenceBean.setLocation(location);
		externalReferenceBean.setThumbnailUrl(thumbnailUrl);
		try {
			BeanUtils.setProperty(externalReferenceBean, "caption", getFieldValue(entry.getContent().getPayload().getField(), "title"));
		} catch (IllegalAccessException | InvocationTargetException e) {
			LOGGER.log(Level.FINEST, "failed to set caption", e);
		}

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

	public FilesAspectBean createFilesAspectBean(ContentFileInfo contentFileInfo) {
		// atex.Files
		FilesAspectBean filesAspectBean = new FilesAspectBean();
		HashMap<String, ContentFileInfo> files = new HashMap<>();
		files.put(contentFileInfo.getFilePath(), contentFileInfo);
		filesAspectBean.setFiles(files);
		return filesAspectBean;
	}

	public ImageInfoAspectBean createImageInfoAspectBean(ContentFileInfo fInfo) {
		// atex.Image
		ImageInfoAspectBean imageInfoAspectBean = new ImageInfoAspectBean();
		imageInfoAspectBean.setFilePath(fInfo.getFilePath());
		return imageInfoAspectBean;
	}

	private String createFileServiceUrl(final String userName, final String filePath) {
		String apiUrl = DamUtils.getRemoteApiUrl();
		if (StringUtils.isBlank(apiUrl)) {
			throw new RuntimeException("Failed to read remote api url");
		}

        return UriBuilder
            .fromPath(apiUrl)
            .path("file")
            .path("tmp")
            .path(FriendlyUrlConverter.convert(userName))
            .path(filePath)
            .build()
            .toASCIIString();
	}

    private WebServiceResponse uploadToFileService(String filePath, InputStream is, String mimeType, String authToken) throws Exception {
        return HttpDamUtils.sendStreamToFileService(
            mimeType,
            is,
            createFileServiceUrl(DamUtils.getRemoteUser(), filePath),
            authToken);
    }

	private static final Gson GSON = new GsonBuilder().create();
	private ContentFileInfo buildContentFileInfo(final String response, final String cleanPath) throws CMException {
		final JsonObject jsonResponse = GSON.fromJson(response, JsonObject.class);
		if(jsonResponse == null || !jsonResponse.has("URI")) {
			throw new CMException(String.format("Invalid file response"));
		}

		final String remoteURI = jsonResponse.get("URI").getAsString();
		final String remotePath = jsonResponse.has("originalPath") ? jsonResponse.get("originalPath").getAsString() : cleanPath;
		return new ContentFileInfo(remotePath, remoteURI);
	}
}

