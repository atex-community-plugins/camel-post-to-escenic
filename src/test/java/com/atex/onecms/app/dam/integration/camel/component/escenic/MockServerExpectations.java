package com.atex.onecms.app.dam.integration.camel.component.escenic;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.mockserver.client.MockServerClient;
import org.mockserver.client.initialize.PluginExpectationInitializer;

import java.util.concurrent.TimeUnit;

public class MockServerExpectations implements PluginExpectationInitializer  {

    public void initializeExpectations(MockServerClient mockServerClient) {
        mockServerClient
                .when(request("/image/slowImage.*").withMethod("GET")
                        .withQueryStringParameter("w", ".*")
                )
                .respond(response("image").withStatusCode(200).withDelay(TimeUnit.SECONDS, 2)
                        .withHeader("X-Original-Image-Height", "1768")
                        .withHeader("X-Original-Image-Width", "1227")
                        .withHeader("X-Rendered-Image-Height", "345")
                        .withHeader("X-Rendered-Image-Width", "240"));


        mockServerClient
                .when(request("/image/fastImage.*").withMethod("GET")
                        .withQueryStringParameter("w", ".*")
                )
                .respond(response("image").withStatusCode(200).withDelay(TimeUnit.SECONDS, 1)
                        .withHeader("X-Original-Image-Height", "1768")
                        .withHeader("X-Original-Image-Width", "1227")
                        .withHeader("X-Rendered-Image-Height", "345")
                        .withHeader("X-Rendered-Image-Width", "240"));
    }
}
