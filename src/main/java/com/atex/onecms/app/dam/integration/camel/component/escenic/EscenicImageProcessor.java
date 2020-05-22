package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.engagement.EngagementDesc;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToDeserializeContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToExtractLocationException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToRetrieveEscenicContentException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.exception.FailedToSendContentToEscenicException;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.*;
import com.atex.onecms.app.dam.standard.aspects.OneImageBean;
import com.atex.onecms.app.dam.util.DamEngagementUtils;
import com.atex.onecms.app.dam.util.ImageUtils;
import com.atex.onecms.content.*;
import com.atex.onecms.content.Content;
import com.atex.onecms.content.aspects.Aspect;
import com.atex.onecms.content.files.FileService;
import com.atex.onecms.image.CropInfo;
import com.atex.onecms.image.ImageEditInfoAspectBean;
import com.atex.onecms.image.ImageInfoAspectBean;
import com.atex.onecms.image.Rectangle;
import com.atex.onecms.ws.image.ImageServiceConfiguration;
import com.atex.onecms.ws.image.ImageServiceConfigurationProvider;
import com.atex.onecms.ws.image.ImageServiceUrlBuilder;
import com.google.common.base.CharMatcher;
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
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class EscenicImageProcessor extends EscenicSmartEmbedProcessor {

	protected final Logger log = LoggerFactory.getLogger(getClass());
	private static EscenicImageProcessor instance;
	public static long MAX_IMAGE_SIZE = 2073600;
	private static int MAX_IMAGE_WIDTH = 1920;
	private static int MAX_IMAGE_HEIGHT = 1080;
	private static double IMAGE_QUALITY = 0.75d;

	private String imageServiceUrl;

	private static final Pattern DOUBLE_SLASH_PATTERN = Pattern.compile("/*/");
	HttpFileServiceClient httpFileServiceClient;




	public EscenicImageProcessor(ContentManager contentManager, PolicyCMServer cmServer, EscenicUtils escenicUtils, EscenicConfig escenicConfig, HttpFileServiceClient httpFileServiceClient, String imageServiceUrl) {
		super(contentManager, cmServer, escenicUtils, escenicConfig);
		this.httpFileServiceClient = httpFileServiceClient;
		this.imageServiceUrl = imageServiceUrl;
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

	protected EscenicImage processImage(ContentId contentId, EscenicImage escenicImage, DamEngagementUtils utils, List<EscenicContent> escenicContentList, String sectionId) {

		ContentResult imgCr =  escenicUtils.checkAndExtractContentResult(contentId, contentManager);

		if (imgCr != null && imgCr.getStatus().isSuccess()) {
			//check if the enagement on the image exists?
			//we'll need to detect the filename used? just in case the user removes and image and uploads a different one for the same desk object..
			//this could cause a problem on the escenic side if the image was already uploaded (the binary file)
			String existingEscenicLocation = null;
			try {
				existingEscenicLocation = getEscenicIdFromEngagement(utils, contentId);
			} catch (CMException e) {
				e.printStackTrace();
			}

			boolean isUpdate = StringUtils.isNotEmpty(existingEscenicLocation);
			List<CloseableHttpResponse> responses = new ArrayList<>();

			Entry escenicImageEntry = null;
			String existingEscenicId = null;
			if (isUpdate) {
				//load image + merge it with existing image and send an update? + process the response?
				existingEscenicId = extractIdFromLocation(existingEscenicLocation);
				if (StringUtils.isNotEmpty(existingEscenicId)) {

					try {
						escenicImageEntry = escenicUtils.generateExistingEscenicEntry(existingEscenicLocation);
					} catch (FailedToRetrieveEscenicContentException | FailedToDeserializeContentException e) {
						e.printStackTrace();
					}
				}
			}
			CloseableHttpResponse response = null;
			try {
				response = processImage(imgCr, escenicImageEntry, existingEscenicLocation,  cmServer, escenicConfig, escenicImage, sectionId);
			} catch (FailedToSendContentToEscenicException e) {
				e.printStackTrace();
			}


			if (response != null) {
				int statusCode = response.getStatusLine().getStatusCode();
				String escenicLocation = null;
				String escenicId = null;
				if (statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_OK ) {

					try {
						escenicLocation = retrieveEscenicLocation(response);
					} catch (IOException | FailedToExtractLocationException e) {
						e.printStackTrace();
					}

					escenicId = extractIdFromLocation(escenicLocation);
				} else if (statusCode == HttpStatus.SC_NO_CONTENT) {
					escenicLocation = existingEscenicLocation;
					escenicId = existingEscenicId;

				} else {
					log.error("The server returned : " + response.getStatusLine() + " when attempting to sendimage id: " + contentId);
					//throw an error here.
				}

				final EngagementDesc engagement = createEngagementObject(escenicId, escenicLocation, getCurrentCaller());
				escenicImage.setEscenicId(escenicId);
				escenicImage.setEscenicLocation(escenicLocation);

				//TODO hack to change the URL from what's provided to a thumbnail url --> alternative is to query for content and read it.
				escenicImage.setThumbnailUrl(escenicLocation.replaceAll("escenic/content", "thumbnail/article"));

				List<Link> links = escenicUtils.generateLinks(escenicImage);
				escenicImage.setLinks(links);

				//atempt to update the engagement
				processEngagement(contentId,engagement,existingEscenicLocation, utils, imgCr);
				return escenicImage;
			}
		}
		return null;
	}

	protected CloseableHttpResponse processImage(ContentResult imgCr, Entry existingImgEntry, String existingEscenicLocation,
												 PolicyCMServer cmServer, EscenicConfig escenicConfig,
												 EscenicImage escenicImage, String sectionId) throws FailedToSendContentToEscenicException {
//		List<CloseableHttpResponse> responses = new ArrayList<>();
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

				String binaryLocation = null;

					//i.e it's a brand new content that doesn't exist in escenic...
					//todo this needs to be reworked.
//					binaryLocation = sendBinary(imgCr.getContent(), oneImageBean, cmServer, binaryUrl, escenicConfig);




				binaryLocation = testSendBinary(imgCr, oneImageBean, cmServer, binaryUrl, escenicConfig, existingImgEntry, imageEditInfoAspectBean);


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
				log.error("Image Bean was null");

			}
		} else {
			log.error("Image cr was null");

		}
		return response;
	}

	private Entry processExistingImage(Entry existingEntry, Entry entry) {
		if (existingEntry != null && entry != null) {
			List<Field> existingFields = existingEntry.getContent().getPayload().getField();
			List<Field> newFields = entry.getContent().getPayload().getField();
			for (Field field : existingFields) {
				for (Field newField : newFields) {
					//modify all fields apart binary location.
					if (StringUtils.isNotBlank(field.getName()) && !StringUtils.equalsIgnoreCase(field.getName(), "binary")) {
						if (StringUtils.equalsIgnoreCase(field.getName(), newField.getName())) {
							field.setValue(newField.getValue());
						}
					}
				}
			}

			existingEntry.setControl(entry.getControl());
			existingEntry.setTitle(entry.getTitle());
//			existingEntry.setLink(entry.getLink());
			List<Link> existingLinks = existingEntry.getLink();
			List<Link> links = entry.getLink();

			if (existingLinks != null && links != null) {
				for (Link existinglink : existingLinks) {

					boolean found = false;

					for (Link link : links) {
						//todo
						if (StringUtils.equalsIgnoreCase(existinglink.getHref(), link.getHref()) &&
							StringUtils.equalsIgnoreCase(existinglink.getIdentifier(), link.getIdentifier()) &&
							StringUtils.equalsIgnoreCase(existinglink.getType(), link.getType())) {

							//it's the same link...
							found = true;
						}
					}

					if (!found && !StringUtils.equalsIgnoreCase(existinglink.getRel(), "related")) {
						links.add(existinglink);

					}
				}
				existingEntry.setLink(links);
			}


			return existingEntry;
		}
		return entry;
	}

	private String clean(String component) {
		if (component == null) return null;

		return CharMatcher.ASCII.retainFrom(component);

	}

	private void updateMaxImageSize() {
		String w = escenicConfig.getMaxImgWidth();
		String h = escenicConfig.getMaxImgHeight();

		if (StringUtils.isNotBlank(w)) {
			try {
				MAX_IMAGE_WIDTH = Integer.parseInt(w);
			} catch (NumberFormatException e) {
				log.warn("Invalid value. Unable to extract max image width from config.");
			}
		}

		if (StringUtils.isNotBlank(h)) {
			try {
				MAX_IMAGE_HEIGHT = Integer.parseInt(h);
			} catch (NumberFormatException e) {
				log.warn("Invalid value. Unable to extract max image heigth from config.");
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
			.height(MAX_IMAGE_HEIGHT)
			.quality(IMAGE_QUALITY);

		try {
			final URL url = new URL(imageServiceUrl + urlBuilder.buildUrl());
			log.info("Resizing image " + contentFileInfo + ", from " + url);

			final HttpResponse httpResponse = httpClient.execute(new HttpGet(url.toURI()));

			final int statusCode = httpResponse.getStatusLine().getStatusCode();
			log.info("Resizing image from " + url + " status code: " + statusCode);

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

	private String testSendBinary(ContentResult imgCr, OneImageBean oneImageBean, PolicyCMServer cmServer, String binaryUrl, EscenicConfig escenicConfig, Entry existingImgEntry, ImageEditInfoAspectBean imageEditInfoAspectBean) {
		String location = null;
		if (imgCr != null) {

			Content cresultContent = imgCr.getContent();


			final FileService fileService = httpFileServiceClient.getFileService();

			FilesAspectBean fab = (FilesAspectBean) cresultContent.getAspectData(FilesAspectBean.ASPECT_NAME);
//			ImageEditInfoAspectBean imageEditInfoAspectBean = (ImageEditInfoAspectBean) cresultContent.getAspectData(ImageEditInfoAspectBean.ASPECT_NAME);
			ImageInfoAspectBean imageInfoAspectBean = (ImageInfoAspectBean) cresultContent.getAspectData(ImageInfoAspectBean.ASPECT_NAME);

			if (imageInfoAspectBean != null) {
				String imgFilePath = imageInfoAspectBean.getFilePath();

				if (StringUtils.isNotBlank(imgFilePath)) {

					if (fab != null) {
						Map<String, ContentFileInfo> map = fab.getFiles();
						if (map != null) {
							try (final CloseableHttpClient httpclient = HttpClients.custom()
								.disableAutomaticRetries()
								.disableCookieManagement()
								.disableContentCompression()
								.build()) {
								for (Map.Entry<String, ContentFileInfo> entry : map.entrySet()) {
									if (entry != null) {
										ContentFileInfo contentFileInfo = entry.getValue();
										if (contentFileInfo != null) {
											if (StringUtils.isNotBlank(contentFileInfo.getFilePath()) && StringUtils.equalsIgnoreCase(contentFileInfo.getFilePath(), imgFilePath)) {


												try (InputStream data = getResizedImageStream(
													escenicUtils.getHttpClient(),
													imgCr,
													imageInfoAspectBean,
													imageEditInfoAspectBean,
													fileService,
													contentFileInfo,
													Subject.NOBODY_CALLER)) {

//												data.available()
													//only send it if there's no existing entry.
												if (existingImgEntry == null) {
													location = sendImage(data, contentFileInfo.getFilePath(), binaryUrl, escenicConfig);
												}

												} catch (CMException e) {
													e.printStackTrace();
												}
											}
										}
									}
								}
							} catch (IOException e) {
								e.printStackTrace();
							}

						}
					}
				}

			}

		}


		return location;

	}


	private String sendImage(InputStream in, String imgExt, String binaryUrl, EscenicConfig escenicConfig) {
		HttpPost request = new HttpPost(binaryUrl);
		InputStreamEntity entity = escenicUtils.generateImageEntity(in, imgExt);
		request.setEntity(entity);
		request.expectContinue();
		request.setHeader(escenicUtils.generateAuthenticationHeader());
		request.setHeader(escenicUtils.generateContentTypeHeader("image/" + imgExt));
		System.out.println("Sending BINARY to escenic:" + in.toString());

		try {
			CloseableHttpResponse result = escenicUtils.getHttpClient().execute(request);


			//TODO HERE evaluate the response properly... then extract the header
			System.out.println(result.getStatusLine());
			//todo probably check the status code?
			for (Header h : result.getAllHeaders()) {
				System.out.println(h.getName() + " : " + h.getValue());
				if (StringUtils.equalsIgnoreCase(h.getName(), "Location")) {
					return h.getValue();
//						constructAtomEntryForBinary()
				}
			}
//				return result;
		} catch (Exception e) {
			System.out.println("Error :" + e.getMessage());
			System.out.println("Error :" + e.getCause());
		} finally {
			request.releaseConnection();
		}
		return null;
	}

	private Entry constructAtomEntryForBinaryImage(OneImageBean oneImageBean, Entry existingImgEntry, Content cresultContent, String binaryLocation, EscenicConfig escenicConfig, ImageEditInfoAspectBean imageEditInfoAspectBean) {
		if (oneImageBean != null) {
			Entry entry = new Entry();
			Title title = new Title();
			title.setType("text");
			if (StringUtils.isEmpty(oneImageBean.getName())) {
				title.setTitle("No Name");
			} else {
				title.setTitle(oneImageBean.getName());
			}


			Payload payload = new Payload();
			com.atex.onecms.app.dam.integration.camel.component.escenic.model.Content content = new com.atex.onecms.app.dam.integration.camel.component.escenic.model.Content();

			Control control = new Control();
			State state = new State();

			state.setName("published");
			//TODO is it needed? try without it..
//			state.setHref("http://inm-test-editorial.inm.lan:8081/webservice/escenic/content/state/published/editor");
			state.setState("published");
//			List<State> states = new ArrayList<>();
//			states.add(state);
			control.setState(Arrays.asList(state));
			control.setDraft("no");

			List<Field> fields = generateImageFields(oneImageBean, cresultContent, binaryLocation, imageEditInfoAspectBean);
			payload.setField(fields);

			//TODO this has to be determined by the type.. if we are going to use the same method for different binary files
//			payload.setModel("http://inm-test-editorial.inm.lan:8081/webservice/escenic/publication/independent/model/content-type/picture");
			payload.setModel(escenicConfig.getModelUrl() + EscenicImage.IMAGE_MODEL_CONTENT_TYPE);

			content.setPayload(payload);
			content.setType(com.atex.onecms.app.dam.integration.camel.component.escenic.model.Content.TYPE);

			entry.setContent(content);
			entry.setControl(control);

			return entry;
		} else {
			//todo.
			throw new RuntimeException("Could not read properties of an image " + cresultContent);
		}
	}

	protected List<Field> generateImageFields(OneImageBean oneImageBean, Content source, String location, ImageEditInfoAspectBean imageEditInfoAspectBean) {
		List<Field> fields = new ArrayList<Field>();

		//todo probably would be easier to create a custom type? that hold some of the info in single place instead of checking mutliple places...
//		FilesAspectBeansource.getContent().getAspect(FilesAspectBean.ASPECT_NAME)
		JSONObject crops = extractCrops(source, imageEditInfoAspectBean);

		if (crops != null) {
			fields.add(escenicUtils.createField("autocrop", crops.toString(), null, null));
		}

		fields.add(escenicUtils.createField("name", oneImageBean.getName(), null, null));
		if (StringUtils.isEmpty(oneImageBean.getName())) {
			fields.add(escenicUtils.createField("title", "No Name", null, null));
		} else {
			fields.add(escenicUtils.createField("title", oneImageBean.getName(), null, null));
		}

		fields.add(escenicUtils.createField("description", oneImageBean.getDescription(), null, null));
		fields.add(escenicUtils.createField("caption", oneImageBean.getCaption(), null, null));
		fields.add(escenicUtils.createField("photographer", oneImageBean.getAuthor(), null, null));
		fields.add(escenicUtils.createField("copyright", oneImageBean.getCredit(), null, null));

		if (StringUtils.isNotBlank(location)) {
			/**
			 *
			 *
			 * TODO BELOW!!
			 */

			Link link = new Link();
			link.setHref(location);
			link.setRel("edit-media");

			Aspect filesAspect = source.getAspect(FilesAspectBean.ASPECT_NAME);
			FilesAspectBean fab = (FilesAspectBean) filesAspect.getData();
			String imgExt = "jpeg";
			for (ContentFileInfo f : fab.getFiles().values()) {
				System.out.println("setting ext : " + f.getFilePath().substring(f.getFilePath().lastIndexOf('.') + 1));
				imgExt = f.getFilePath().substring(f.getFilePath().lastIndexOf('.') + 1);
			}
			if (StringUtils.equalsIgnoreCase(imgExt, "jpg")) {
				imgExt = "jpeg";
			}
			link.setType("image/" + imgExt);

			/**
			 *
			 *
			 * TODO ABOVE!!
			 */

			link.setTitle(oneImageBean.getName());
			fields.add(escenicUtils.createField("binary", link, null, null));
		}
		return fields;
	}


	private JSONObject extractCrops(Content content, ImageEditInfoAspectBean imageEditInfoAspectBean) {
//<vdf:field name="crops">
//<vdf:value>{"com.escenic.master":{"crop":{"x":0,"y":0,"width":1566,"height":1175.005582526925,"auto":false}},"poi":{"top":503.74849115618434,"left":863.2195018355974,"auto":false},"T_ATop_w726h400-2x":{"crop":{"x":0,"y":118.8057719540687,"width":1566,"height":862.809917355372,"auto":false}},"T_Th_w229h155-2x":{"crop":{"x":0,"y":0,"width":1566,"height":1059.9563318777293,"auto":true}},"T_Sq_w478h473-2x":{"crop":{"x":269.5063216581448,"y":0,"width":1187.4263603549052,"height":1175.005582526925,"auto":true}},"T_FullW_w768h652-2x":{"crop":{"x":171.19167408967834,"y":0,"width":1384.055655491838,"height":1175.005582526925,"auto":true}},"T_ColTh_w100square-2x":{"crop":{"x":275.71671057213484,"y":0,"width":1175.005582526925,"height":1175.005582526925,"auto":true}},"mW_Article_w320-2x":{"crop":{"x":0,"y":0,"width":1566,"height":1175.005582526925,"auto":true}},"mW_Article_w320-1x5":{"crop":{"x":0,"y":0,"width":1566,"height":1175.005582526925,"auto":true}},"mW_Article_w320-1x":{"crop":{"x":0,"y":0,"width":1566,"height":1175.005582526925,"auto":true}},"T_Article_w310-2x":{"crop":{"x":0,"y":0,"width":1566,"height":1175.005582526925,"auto":true}},"W_Inline_w620-1x":{"crop":{"x":0,"y":0,"width":1566,"height":1175.005582526925,"auto":true}},"T_Gallery_w768-2x":{"crop":{"x":0,"y":0,"width":1566,"height":1175.005582526925,"auto":true}},"T_Col_w479h151-2x":{"crop":{"x":0,"y":256.91550576996303,"width":1566,"height":493.6659707724426,"auto":true}}}</vdf:value>
//</vdf:field>


		if (content != null) {


			if (imageEditInfoAspectBean != null) {
				Map<String, CropInfo> crops = imageEditInfoAspectBean.getCrops();
				//loop over them?
				if (crops != null) {
					JSONObject obj = new JSONObject();
					for (Map.Entry<String, CropInfo> entry : crops.entrySet()) {
						String key = entry.getKey();
						CropInfo value = entry.getValue();

						if (StringUtils.isNotBlank(key) && value != null) {

							JSONObject cropObject = new JSONObject();

							Rectangle cropRectangle = value.getCropRectangle();
							if (cropRectangle != null) {
								JSONObject crop = new JSONObject();
								try {
									crop.put("x", cropRectangle.getX());
									crop.put("y", cropRectangle.getY());
									crop.put("height", cropRectangle.getHeight());
									crop.put("width", cropRectangle.getWidth());
									crop.put("auto", false);
									cropObject.put("crop", crop);

									obj.put(getCropKey(key), cropObject);
								} catch (JSONException e) {
									log.error("failed to generate json for crops: " + e);
								}
							}
						}
					}

					return obj;
				}
			} else {
				log.error("Unable to extract crop information for content: " + content.getId());
			}


		}
		return null;

	}

	//todo configurable?
	private static String getCropKey(String key) {
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
