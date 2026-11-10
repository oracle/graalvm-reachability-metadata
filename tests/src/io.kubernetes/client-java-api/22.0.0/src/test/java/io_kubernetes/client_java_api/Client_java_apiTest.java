/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

public class Client_java_apiTest {
    @Test
    @Timeout(120)
    void listsServicesAndEndpointsThroughCoreApi() throws Exception {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                    .setBody(ModelJson.SERVICE_LIST));
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                    .setBody(ModelJson.ENDPOINTS_LIST));
            server.start();
            String basePath = server.url("/").toString();
            ApiClient client = new ApiClient(httpClient).setBasePath(basePath.substring(0, basePath.length() - 1));
            CoreV1Api api = new CoreV1Api(client);

            V1ServiceList services = api.listNamespacedService("apps").execute();
            assertThat(services.getItems()).hasSize(1);
            assertThat(services.getItems().get(0).getSpec().getSelector()).containsEntry("app", "web");
            assertThat(services.getItems().get(0).getSpec().getPorts().get(0).getTargetPort().getStrValue())
                    .isEqualTo("http");
            ModelJson.assertJsonEquals(ModelJson.SERVICE_LIST, services.toJson());

            V1EndpointsList endpoints = api.listNamespacedEndpoints("apps").execute();
            assertThat(endpoints.getItems()).hasSize(1);
            assertThat(endpoints.getItems().get(0).getSubsets().get(0).getAddresses().get(0).getNodeName())
                    .isEqualTo("worker-a");
            ModelJson.assertJsonEquals(ModelJson.ENDPOINTS_LIST, endpoints.toJson());

            RecordedRequest servicesRequest = server.takeRequest(10, TimeUnit.SECONDS);
            assertThat(servicesRequest).isNotNull();
            assertThat(servicesRequest.getMethod()).isEqualTo("GET");
            assertThat(servicesRequest.getPath()).isEqualTo("/api/v1/namespaces/apps/services");
            RecordedRequest endpointsRequest = server.takeRequest(10, TimeUnit.SECONDS);
            assertThat(endpointsRequest).isNotNull();
            assertThat(endpointsRequest.getMethod()).isEqualTo("GET");
            assertThat(endpointsRequest.getPath()).isEqualTo("/api/v1/namespaces/apps/endpoints");
        } finally {
            httpClient.connectionPool().evictAll();
            httpClient.dispatcher().executorService().shutdownNow();
        }
    }
}
