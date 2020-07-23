package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.*;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.*;
import com.atex.onecms.app.dam.standard.aspects.OneImageBean;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.app.dam.util.ImageUtils;
import com.atex.onecms.content.*;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.files.FileService;
import com.atex.onecms.image.*;
import com.atex.onecms.ws.image.ImageServiceConfiguration;
import com.atex.onecms.ws.image.ImageServiceConfigurationProvider;
import com.atex.onecms.ws.image.ImageServiceUrlBuilder;
import com.atex.plugins.baseline.util.MimeUtil;
import com.google.gson.JsonObject;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.client.HttpFileServiceClient;
import com.polopoly.cm.policy.PolicyCMServer;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class EscenicImageProcessor extends EscenicSmartEmbedProcessor {

	private static final Logger LOGGER = Logger.getLogger(EscenicImageProcessor.class.getName());
	private static EscenicImageProcessor instance;
	private static int MAX_IMAGE_WIDTH = 16000;
	private static double IMAGE_QUALITY = 0.75d;

	protected static final String EDIT_MEDIA_RELATIONSHIP = "edit-media";
	private String imageServiceUrl;

	private Map<String, String> cropsMapping;
	private static final Pattern DOUBLE_SLASH_PATTERN = Pattern.compile("/*/");
	HttpFileServiceClient httpFileServiceClient;



	public EscenicImageProcessor(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig, HttpFileServiceClient httpFileServiceClient, String imageServiceUrl) {
		super(contentManager, cmServer, escenicUtils, escenicConfig);
		this.httpFileServiceClient = httpFileServiceClient;
		this.imageServiceUrl = imageServiceUrl;
		this.cropsMapping = initCrops();
	}

	public static EscenicImageProcessor getInstance() {
		if (instance == null) {
			throw new RuntimeException("EscenicImageProcessor not initialized");
		}
		return instance;
	}

	public static synchronized void initInstance(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig, HttpFileServiceClient httpFileServiceClient, String imageServiceUrl) {
		if (instance == null) {
			instance = new EscenicImageProcessor(contentManager, cmServer, escenicUtils, escenicConfig, httpFileServiceClient,imageServiceUrl);
		}
	}

	private Map initCrops() throws RuntimeException {
		String cropsString = escenicConfig.getCropsMapping();
		if (StringUtils.isBlank(cropsString)) {
			throw new RuntimeException("Missing configuration for crop mappings between desk & escenic");
		}

		try {
			String[] cropsArray = cropsString.split(",");
			HashMap<String, String> map = new HashMap();
			for (String s : cropsArray) {
				String[] cropDefinition = s.split(":");
				map.put(cropDefinition[0], cropDefinition[1]);
			}

			return map;

		} catch (Exception e) {
			throw new RuntimeException("Failed to process crops mapping from configuration.");
		}
	}

	protected EscenicImage processImage(ContentId contentId, EscenicImage escenicImage, DamEngagementUtils utils, List<EscenicContent> escenicContentList, String sectionId, String action) throws EscenicException {

		ContentResult imgCr =  escenicUtils.checkAndExtractContentResult(contentId, contentManager);

		if (imgCr != null && imgCr.getStatus().isSuccess()) {
			OneImageBean oneImageBean = null;
			try {
				 oneImageBean = (OneImageBean) escenicUtils.extractContentBean(imgCr);

			} catch (Exception e) {
				throw new RuntimeException("Failed to convert content result to an image : " + imgCr.getContentId());
			}

			String existingEscenicLocation = null;
			try {
				existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
			} catch (CMException e) {
				throw new RuntimeException("Failed to retreive escenic id from engagement for id: " + contentId);
			}

			boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);
			List<CloseableHttpResponse> responses = new ArrayList<>();

			Entry escenicImageEntry = null;
			String existingEscenicId = null;
			if (isUpdate) {
				existingEscenicId = escenicUtils.extractIdFromLocation(existingEscenicLocation);
				if (StringUtils.isNotEmpty(existingEscenicId)) {

					try {
						escenicImageEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation);
					} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
						throw new EscenicException("Failed to generate existing image entry for " + contentId);
					}
				}
			}

			CloseableHttpResponse response = processImage(imgCr, escenicImageEntry, existingEscenicLocation,  cmServer, escenicConfig, escenicImage, sectionId);

			EngagementDesc engagementDesc = evaluateResponse(contentId, existingEscenicLocation, existingEscenicId, true, response, utils, imgCr, action);

			String escenicId = escenicUtils.getEscenicIdFromEngagement(engagementDesc, existingEscenicId);
			String escenicLocation = escenicUtils.getEscenicLocationFromEngagement(engagementDesc, existingEscenicLocation);
			assignProperties(oneImageBean, escenicImage, escenicId, escenicLocation, contentId);
			return escenicImage;
		}
		return null;
	}

	protected CloseableHttpResponse processImage(ContentResult imgCr, Entry existingImgEntry, String existingEscenicLocation,
												 PolicyCMServer cmServer, EscenicConfig escenicConfig,
												 EscenicImage escenicImage, String sectionId) throws FailedToSendContentToEscenicException, EscenicResponseException {
		CloseableHttpResponse response = null;
		String binaryUrl = escenicConfig.getBinaryUrl();
		if (StringUtils.isEmpty(binaryUrl)) {
			throw new RuntimeException("Unable to send image to Escenic as binaryUrl is blank");
		}

		if (imgCr != null) {
			Object contentBean = escenicUtils.extractContentBean(imgCr);
			OneImageBean oneImageBean = null;
			if (contentBean != null && contentBean instanceof OneImageBean) {
				oneImageBean = (OneImageBean) contentBean;
			}

			if (oneImageBean != null) {

				if (StringUtils.isNotEmpty(oneImageBean.getTitle())) {
					escenicImage.setTitle(oneImageBean.getName());
				} else {
					escenicImage.setTitle("No title");
				}

				ImageEditInfoAspectBean imageEditInfoAspectBean = (ImageEditInfoAspectBean) imgCr.getContent().getAspectData(ImageEditInfoAspectBean.ASPECT_NAME);
				String binaryLocation = sendBinaryImage(imgCr, oneImageBean, cmServer, binaryUrl, escenicConfig, existingImgEntry, imageEditInfoAspectBean);
				Entry atomEntry = constructAtomEntryForBinaryImage(oneImageBean, existingImgEntry, imgCr.getContent(), binaryLocation, escenicConfig, imageEditInfoAspectBean);
				if (existingImgEntry != null) {
					atomEntry = processExistingImage(existingImgEntry, atomEntry);
				}
				String xml = escenicUtils.serializeXml(atomEntry);
				if (StringUtils.isNotEmpty(xml) && existingImgEntry != null) {
					response = escenicUtils.sendUpdatedContentToEscenic(existingEscenicLocation, xml);
				} else {
					response = escenicUtils.sendNewContentToEscenic(xml, sectionId);
				}

			} else {
				LOGGER.severe("Was unable to process an image: Image Bean was null");

			}
		} else {
			LOGGER.severe("Was unable to process an image: content result was null");

		}
		return response;
	}

	private Entry processExistingImage(Entry existingEntry, Entry entry) {
		if (existingEntry != null && entry != null) {
			List<Field> existingFields = existingEntry.getContent().getPayload().getField();
			List<Field> newFields = entry.getContent().getPayload().getField();
			for (Field field : existingFields) {
				for (Field newField : newFields) {
					//modify all fields but binary location.
					if (StringUtils.isNotBlank(field.getName()) && !StringUtils.equalsIgnoreCase(field.getName(), "binary")) {
						if (StringUtils.equalsIgnoreCase(field.getName(), newField.getName())) {
							field.setValue(newField.getValue());
						}
					}
				}
			}

			existingEntry.setControl(entry.getControl());
			existingEntry.setTitle(entry.getTitle());
			existingEntry.setLink(escenicUtils.mergeLinks(existingEntry.getLink(), entry.getLink()));
			//we're resetting the summary to ensure invalid xhtml chars are being escaped
			escenicUtils.cleanUpSummary(existingEntry);
			return existingEntry;
		}
		return entry;
	}

	private void updateMaxImageSize() {
		String w = escenicConfig.getMaxImgWidth();

		if (StringUtils.isNotBlank(w)) {
			try {
				MAX_IMAGE_WIDTH = Integer.parseInt(w);
			} catch (NumberFormatException e) {
				LOGGER.warning("Invalid value. Unable to extract max image width from config.");
			}
		}

	}

	protected <R> BufferedInputStream getResizedImageStream(final HttpClient httpClient,
													ContentResult<R> cr,
													ImageInfoAspectBean imageInfoAspectBean,
													ImageEditInfoAspectBean imageEditInfoAspectBean,
													FileService fileService,
													ContentFileInfo contentFileInfo,
													Subject subject) throws CMException, IOException {

		updateMaxImageSize();

		if (imageInfoAspectBean == null || imageInfoAspectBean.getWidth() <= MAX_IMAGE_WIDTH) {
			return new BufferedInputStream(fileService.getFile(contentFileInfo.getFileUri(), subject));
		}
		int rotation = 0;
		boolean flipVertical = false;
		boolean flipHorizontal = false;

		if (imageEditInfoAspectBean != null) {
			rotation = imageEditInfoAspectBean.getRotation();
			flipVertical = imageEditInfoAspectBean.isFlipVertical();
			flipHorizontal = imageEditInfoAspectBean.isFlipHorizontal();
		}

		Rectangle dimensions =
			ImageUtils.rotateAndFlipRectangle(new Rectangle(0, 0, imageInfoAspectBean.getWidth(), imageInfoAspectBean.getHeight()),
				rotation, new java.awt.Dimension(imageInfoAspectBean.getWidth(), imageInfoAspectBean.getHeight()),
				flipVertical, flipHorizontal);

		final ImageServiceConfiguration imageServiceConfiguration = new ImageServiceConfigurationProvider(cmServer).getImageServiceConfiguration();

		// By default the image service will also sample the quality to .75 so, bump it to 1.0 so we do not double sample
		final ImageServiceUrlBuilder urlBuilder = new ImageServiceUrlBuilder(cr, imageServiceConfiguration.getSecret())
			.width(MAX_IMAGE_WIDTH)
			.quality(IMAGE_QUALITY);

		try {
			final URL url = new URL(imageServiceUrl + urlBuilder.buildUrl());
			LOGGER.info("Resizing image " + contentFileInfo + ", from " + url);

			final HttpResponse httpResponse = httpClient.execute(new HttpGet(url.toURI()));

			final int statusCode = httpResponse.getStatusLine().getStatusCode();
			LOGGER.info("Resizing image from " + url + " status code: " + statusCode);

			if (statusCode < 200 || statusCode >= 400) {
				throw new IOException("Resize image failed with status code: " + statusCode);
			}

			final int newWidth = Optional.ofNullable(httpResponse.getFirstHeader("X-Rendered-Image-Width"))
				.map(Header::getValue)
				.map(Integer::parseInt)
				.orElse(0);
			final int newHeight = Optional.ofNullable(httpResponse.getFirstHeader("X-Rendered-Image-Height"))
				.map(Header::getValue)
				.map(Integer::parseInt)
				.orElse(0);

			LOGGER.info("Image after resizing width: " + newWidth + " and height: " + newHeight);

			final float widthRatio =  (float) newWidth / dimensions.getWidth();
			final float heightRatio = (float) newHeight / dimensions.getHeight();

			if (imageEditInfoAspectBean != null) {
				normalizeCrop(imageEditInfoAspectBean, newWidth, newHeight, widthRatio, heightRatio);
			}

			// ADM-375
			// when we resize an image we should also normalize the image crop
			// inside collections.

//			Optional.ofNullable(collectionAspectBean)
//				.map(CollectionAspect::getCollections)
//				.map(Collection::stream)
//				.orElse(Stream.empty())
//				.filter(c -> c.getCrop() != null)
//				.map(com.atex.onecms.app.dam.collection.aspect.Collection::getCrop)
//				.forEach(i -> normalizeCrop(i, newWidth, newHeight, widthRatio, heightRatio));

			imageInfoAspectBean.setHeight(newHeight);
			imageInfoAspectBean.setWidth(newWidth);
//
			return new BufferedInputStream(httpResponse.getEntity().getContent());
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}

	private void normalizeCrop(final ImageEditInfoAspectBean imageEditInfo,
							   final int newWidth,
							   final int newHeight,
							   final float widthRatio,
							   final float heightRatio) {
		imageEditInfo.getCrops().values().forEach(crop -> {
			Rectangle cropRectangle = crop.getCropRectangle();


			cropRectangle.setHeight(Math.round(cropRectangle.getHeight() * heightRatio));
			cropRectangle.setWidth(Math.round(cropRectangle.getWidth() * widthRatio));
			cropRectangle.setX(Math.round(cropRectangle.getX() * widthRatio));
			cropRectangle.setY(Math.round(cropRectangle.getY() * heightRatio));

			crop.setCropRectangle(ImageUtils.cropRect(new java.awt.Dimension(newWidth, newHeight), cropRectangle, imageEditInfo));

		});
		// Image retrieved by the image server already contains the pixelation & rotation - no need to re-apply on the web, so just remove it.
		imageEditInfo.getPixelations().clear();
		imageEditInfo.setRotation(0);
		imageEditInfo.setFlipHorizontal(false);
		imageEditInfo.setFlipVertical(false);
	}

	private String cleanPath(final String path) {
		// return immediately if path is null
		if (path == null) {
			return null;
		}
		String cleanPath = DOUBLE_SLASH_PATTERN.matcher(path).replaceAll("/");
		if (cleanPath.startsWith("/")) {
			cleanPath = cleanPath.substring(1);
		}
		return cleanPath;
	}

	private String sendBinaryImage(ContentResult imgCr, OneImageBean oneImageBean, PolicyCMServer cmServer, String binaryUrl, EscenicConfig escenicConfig,
								   Entry existingImgEntry, ImageEditInfoAspectBean imageEditInfoAspectBean) throws EscenicResponseException {
		String location = null;
		if (imgCr != null) {
			Content cresultContent = imgCr.getContent();
			final FileService fileService = httpFileServiceClient.getFileService();

			FilesAspectBean fab = (FilesAspectBean) cresultContent.getAspectData(FilesAspectBean.ASPECT_NAME);
			ImageInfoAspectBean imageInfoAspectBean = (ImageInfoAspectBean) cresultContent.getAspectData(ImageInfoAspectBean.ASPECT_NAME);

			if (imageInfoAspectBean != null) {
				String imgFilePath = imageInfoAspectBean.getFilePath();

				if (StringUtils.isNotBlank(imgFilePath)) {

					if (fab != null) {
						Map<String, ContentFileInfo> map = fab.getFiles();
						if (map != null) {
							try {
								for (Map.Entry<String, ContentFileInfo> entry : map.entrySet()) {
									if (entry != null) {
										ContentFileInfo contentFileInfo = entry.getValue();
										if (contentFileInfo != null) {
											if (StringUtils.isNotBlank(contentFileInfo.getFilePath()) && StringUtils.equalsIgnoreCase(contentFileInfo.getFilePath(), imgFilePath)) {

												InputStream data = getResizedImageStream(
													escenicUtils.getHttpClient(),
													imgCr,
													imageInfoAspectBean,
													imageEditInfoAspectBean,
													fileService,
													contentFileInfo,
													Subject.NOBODY_CALLER);

													//only send it if there's no existing entry.
													if (existingImgEntry == null) {
														location = sendImage(data, contentFileInfo.getFilePath(), binaryUrl);
													}
											}
										}
									}
								}
							} catch (IOException | CMException e) {
								throw new RuntimeException("Failed to send binary to escenic" + imgCr.getContentId());
							}
						}
					}
				}
			}
		}
		return location;
	}

	private String sendImage(InputStream in, String imgExt, String binaryUrl) throws RuntimeException, EscenicResponseException {
		HttpPost request = new HttpPost(binaryUrl);
		String mimeType = MimeUtil.getMimeType(imgExt).orElse(null);
		InputStreamEntity entity = escenicUtils.generateImageEntity(in, mimeType);
		request.setEntity(entity);
		request.expectContinue();
		request.setHeader(escenicUtils.generateAuthenticationHeader(escenicConfig.getUsername(), escenicConfig.getPassword()));
		request.setHeader(escenicUtils.generateContentTypeHeader(mimeType));
		LOGGER.info("Sending binary image to escenic");

		try {
			CloseableHttpResponse result = escenicUtils.getHttpClient().execute(request);
			int statusCode = result.getStatusLine().getStatusCode();
			if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_NO_CONTENT) {

				Header header = result.getFirstHeader("Location");
				return header.getValue();
			} else {
				throw new EscenicResponseException("Failed to send the binary to escenic: " + result.getStatusLine());
			}
		} catch (Exception e) {
			throw new EscenicResponseException("Failed to send the binary to escenic: " + e);
		} finally {
			request.releaseConnection();
		}
	}

	private Entry constructAtomEntryForBinaryImage(OneImageBean oneImageBean, Entry existingImgEntry, Content cresultContent, String binaryLocation, EscenicConfig escenicConfig, ImageEditInfoAspectBean imageEditInfoAspectBean) {
		if (oneImageBean != null) {
			Entry entry = new Entry();
			Title title = escenicUtils.createTitle(oneImageBean.getName(), "text");
			entry.setTitle(title);

			Payload payload = new Payload();
			com.atex.onecms.app.dam.integration.camel.component.escenic.model.Content content = new com.atex.onecms.app.dam.integration.camel.component.escenic.model.Content();
			Control control = escenicUtils.generateControl("no", PUBLISHED_STATE);
			List<Field> fields = generateImageFields(oneImageBean, cresultContent, binaryLocation, imageEditInfoAspectBean);
			payload.setField(fields);
			payload.setModel(escenicConfig.getModelUrl() + EscenicImage.IMAGE_MODEL_CONTENT_TYPE);
			content.setPayload(payload);
			content.setType(com.atex.onecms.app.dam.integration.camel.component.escenic.model.Content.TYPE);
			entry.setContent(content);
			entry.setControl(control);

			return entry;
		} else {
			throw new RuntimeException("Could not read properties of an image " + cresultContent);
		}
	}

	protected List<Field> generateImageFields(OneImageBean oneImageBean, Content source, String location, ImageEditInfoAspectBean imageEditInfoAspectBean) {
		List<Field> fields = new ArrayList<Field>();
		JsonObject crops = extractCrops(source, imageEditInfoAspectBean);
		if (crops != null) {
			fields.add(escenicUtils.createField("autocrop", crops.toString(), null, null));
		}

		fields.add(escenicUtils.createField("title", oneImageBean.getName(), null, null));
		fields.add(escenicUtils.createField("description", oneImageBean.getDescription(), null, null));
		fields.add(escenicUtils.createField("photographer", oneImageBean.getCredit(), null, null));
		fields.add(escenicUtils.createField("caption", oneImageBean.getCaption(), null, null));

		if (StringUtils.isNotBlank(location)) {
			Link link = new Link();
			link.setHref(location);
			link.setRel(EDIT_MEDIA_RELATIONSHIP);

			ImageInfoAspectBean info = (ImageInfoAspectBean) source.getAspect(ImageInfoAspectBean.ASPECT_NAME).getData();
			String imgExt = "jpeg";
			if (info != null) {
				String path = info.getFilePath();
				if (StringUtils.isNotBlank(path)) {
					imgExt = path.substring(path.lastIndexOf('.') + 1);
					if (StringUtils.equalsIgnoreCase(imgExt, "jpg")) {
						imgExt = "jpeg";
					}
				}
			}

			if (!StringUtils.equalsIgnoreCase(imgExt, "jpeg") || !StringUtils.equalsIgnoreCase(imgExt, "gif") || !StringUtils.equalsIgnoreCase(imgExt, "png")) {
				//default value in case the extracted value is invalid
				imgExt = "jpeg";
			}

			link.setType("image/" + imgExt);
			link.setTitle(oneImageBean.getName());
			fields.add(escenicUtils.createField("binary", link, null, null));
		}
		return fields;
	}

	protected void assignProperties(OneImageBean oneImageBean, EscenicImage escenicImage, String escenicId, String escenicLocation, ContentId contentId) {
		escenicImage.setEscenicId(escenicId);
		escenicImage.setEscenicLocation(escenicLocation);
		escenicImage.setOnecmsContentId(contentId);
		escenicImage.setThumbnailUrl(escenicLocation.replaceAll("escenic/content", "thumbnail/article"));
		escenicImage.setTitle(oneImageBean.getName());
		escenicImage.setCaption(oneImageBean.getCaption());
		List<Link> links = escenicUtils.generateLinks(escenicImage);
		escenicImage.setLinks(links);
	}

	private JsonObject extractCrops(Content content, ImageEditInfoAspectBean imageEditInfoAspectBean) throws RuntimeException {
		if (content != null) {
			if (imageEditInfoAspectBean != null) {
				Map<String, CropInfo> crops = imageEditInfoAspectBean.getCrops();
				if (crops != null) {
					JsonObject obj = new JsonObject();
					for (Map.Entry<String, CropInfo> entry : crops.entrySet()) {
						String key = entry.getKey();
						CropInfo value = entry.getValue();

						if (StringUtils.isNotBlank(key) && value != null) {

							JsonObject cropObject = new JsonObject();

							Rectangle cropRectangle = value.getCropRectangle();
							if (cropRectangle != null) {
								JsonObject crop = new JsonObject();
								try {
									crop.addProperty("x", cropRectangle.getX());
									crop.addProperty("y", cropRectangle.getY());
									crop.addProperty("height", cropRectangle.getHeight());
									crop.addProperty("width", cropRectangle.getWidth());
									crop.addProperty("auto", false);
									cropObject.add("crop", crop);
									obj.add(getCropKey(key), cropObject);
								} catch(Exception e) {
									throw new RuntimeException("Failed to generate crops : " + e);
								}
							}
						}
					}

					return obj;
				}
			}
		}
		return null;
	}

	private String getCropKey(String key) {

		String value = cropsMapping.get(key);
		if (StringUtils.isBlank(value)) {
			LOGGER.warning("Value for key: " + key + " not found in the crop mapping. Attempting to return from defaults.");
			return getDefaultCrop(key);
		}
		return value;
	}

	private String getDefaultCrop(String key) {
		switch (key) {
			case "2x1":
				return "wide";
			case "3x2":
				return "landscape";
			case "1x1":
				return "square";
			case "2x3":
				return "portrait";
			default:
				return "free";
		}
	}

}
