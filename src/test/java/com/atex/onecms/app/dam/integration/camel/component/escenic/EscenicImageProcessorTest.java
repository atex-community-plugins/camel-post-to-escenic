package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.content.ContentFileInfo;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileService;
import com.atex.onecms.image.*;
import com.google.gson.Gson;
import com.polopoly.cm.client.CMException;
import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class EscenicImageProcessorTest {

    @Mock
    EscenicImageProcessor mockProcessor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void getResizedImageStreamTest() throws CMException, IOException, URISyntaxException {

        // creating imageEditInfoAspectBean with bad data, to test for nullPointers
        String jsonTemplate = IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("atex.ImageEditInfo.json"));
        Gson gson = new Gson();
        ImageEditInfoAspectBean imageEditInfoAspectBean = gson.fromJson(jsonTemplate, ImageEditInfoAspectBean.class);

        ContentResult<?> cr = null;
        ImageInfoAspectBean imageInfoAspectBean = new ImageInfoAspectBean();
        FileService fileService = null;
        ContentFileInfo contentFileInfo = new ContentFileInfo("test", "test2");
        Subject subject = Subject.NOBODY_CALLER;
        EscenicConfig escenicConfig = new EscenicConfig();
        escenicConfig.setCropsMapping("2x1\\:wide,3x2\\:landscape,1x1\\:square,2x3\\:portrait,default\\:free");
        // default timeout set at 60s, reducing it just for testing speed
        escenicConfig.setTimeout(1500);

        // MaxImgWidth just needs to be smaller than Bean width in order for the method to run
        escenicConfig.setMaxImgWidth("200");
        imageInfoAspectBean.setWidth(201);
        imageInfoAspectBean.setHeight(201);

        EscenicUtils eu = new EscenicUtils(escenicConfig, null, null);

        doCallRealMethod().when(mockProcessor).setEscenicUtils(any());
        doCallRealMethod().when(mockProcessor).setEscenicConfig(any());
        doCallRealMethod().when(mockProcessor).setImageServiceUrl(any());
        doCallRealMethod().when(mockProcessor).normalizeCrop(any(), anyInt(), anyInt(), anyFloat(), anyFloat());

        mockProcessor.setEscenicUtils(eu);
        mockProcessor.setEscenicConfig(escenicConfig);
        mockProcessor.setImageServiceUrl("http://localhost:8885");

        //Method being tested
        when(mockProcessor.getResizedImageStream(any(), any(), any(), any(), any(), any(), any())).thenCallRealMethod();

        // Test 1 - should not time out
        // http timeout set to 1.5s, request delay set at 1.0s
        when(mockProcessor.getImageServiceUrl(any(), anyInt())).thenReturn("/image/fastImage?w=100");
        int success = 0;
        int failure = 0;

        // loop to make sure http connections are being released on success
        for (int i = 0; i < EscenicUtils.MAX_CONNECTIONS + 1; i++) {

            try (InputStream data = mockProcessor.getResizedImageStream(eu.getHttpClient(),
                    cr,
                    imageInfoAspectBean,
                    imageEditInfoAspectBean,
                    fileService,
                    contentFileInfo,
                    subject)) {
                success++;
            } catch (Exception e) {
                failure++;
                System.out.println(e);
            }
        }
        // all http requests should succeed, 'success' should be MAX_CONNECTIONS+1
        assertEquals(EscenicUtils.MAX_CONNECTIONS + 1, success);
        assertEquals(0, failure);


        // Test 2 - should time out
        // http timeout set to 1.5s, request delay set at 2.0s
        when(mockProcessor.getImageServiceUrl(any(), anyInt())).thenReturn("/image/slowImage?w=100");
        success = 0;
        failure = 0;

        // loop to make sure http connections are being released on failure
        for (int i = 0; i < EscenicUtils.MAX_CONNECTIONS + 1; i++) {

            try (InputStream data = mockProcessor.getResizedImageStream(eu.getHttpClient(),
                    cr,
                    imageInfoAspectBean,
                    imageEditInfoAspectBean,
                    fileService,
                    contentFileInfo,
                    subject)) {
                success++;
            } catch (Exception e) {
                failure++;
                System.out.println(e);
            }
        }
        // all http requests should time out, 'success' should be 0
        assertEquals(0, success);
        assertEquals(EscenicUtils.MAX_CONNECTIONS + 1, failure);


    }

    @Test
    public void normalizeCropTest() {
        EscenicImageProcessor eIP = new EscenicImageProcessor();

        // setup for ImageEditInfoAspectBean crops
        Rectangle rect1 = new Rectangle(0, 0, 200, 200);
        Rectangle rect2 = new Rectangle(0, 200, 160, 90);
        AspectRatio ratio1 = new AspectRatio(4, 3);
        AspectRatio ratio2 = new AspectRatio(16, 9);
        ImageFormat format1 = new ImageFormat("ImageFormat1", ratio1);
        ImageFormat format2 = new ImageFormat("ImageFormat2", ratio2);
        CropInfo crop1 = new CropInfo(rect1, format1);
        CropInfo crop2 = new CropInfo(rect2, format2);
        Map<String, CropInfo> crops = new HashMap();
        crops.put("Crop1", crop1);
        crops.put("Crop2", crop2);

        ImageEditInfoAspectBean bean1 = new ImageEditInfoAspectBean();
        ImageEditInfoAspectBean bean2 = new ImageEditInfoAspectBean();
        ImageEditInfoAspectBean bean3 = new ImageEditInfoAspectBean();

        bean1.setCrops(crops);

        // running method for testing
        eIP.normalizeCrop(bean1, 1600, 900, 16, 9);
        eIP.normalizeCrop(bean2, 0, 0, 0, 0);
        eIP.normalizeCrop(bean3, 0, 0, 0, 0);

        String expected1 = "[cropRectangle: [x: 0, y: 0, width: 3200, height: 1800], imageFormat: [name: 'ImageFormat1', aspectRatio: [AspectRatio: width=4, height=3]]]";
        String expected2 = "[cropRectangle: [x: 0, y: 1800, width: 2560, height: 810], imageFormat: [name: 'ImageFormat2', aspectRatio: [AspectRatio: width=16, height=9]]]";

        // asserting on bean1
        assertEquals(expected1, bean1.getCrops().get("Crop1").toString());
        assertEquals(expected2, bean1.getCrops().get("Crop2").toString());
        assertEquals(false, bean1.isFlipHorizontal());
        assertEquals(0, bean1.getRotation());
        assertEquals(Collections.emptyList(), bean1.getPixelations());

        // asserting on bean2
        assertEquals(Collections.emptyMap(), bean2.getCrops());
        assertEquals(false, bean2.isFlipVertical());
        assertEquals(0, bean2.getRotation());
        assertEquals(Collections.emptyList(), bean2.getPixelations());

        // asserting on bean3
        assertEquals(Collections.emptyMap(), bean3.getCrops());
    }
}