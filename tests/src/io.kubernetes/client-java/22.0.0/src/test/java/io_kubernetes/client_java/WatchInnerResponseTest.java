/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.util.Watch;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

public class WatchInnerResponseTest {
    @Test
    @Timeout(120)
    void decodesTypedConfigMapEventsFromAWatchStream() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                    {"type":"ADDED","object":{"apiVersion":"v1","kind":"ConfigMap","metadata":{"name":"sync-settings","namespace":"apps","resourceVersion":"41"},"data":{"mode":"active"}}}
                    {"type":"MODIFIED","object":{"apiVersion":"v1","kind":"ConfigMap","metadata":{"name":"sync-settings","namespace":"apps","resourceVersion":"42"},"data":{"mode":"passive"}}}
                    """));
            server.start();
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .callTimeout(30, TimeUnit.SECONDS)
                    .build();
            try {
                String basePath = server.url("/").toString();
                ApiClient client = new ApiClient(httpClient).setBasePath(basePath.substring(0, basePath.length() - 1));
                CoreV1Api api = new CoreV1Api(client);
                Call call = api.listNamespacedConfigMap("apps").watch(true).buildCall(null);
                Type responseType = new TypeToken<Watch.Response<V1ConfigMap>>() { }.getType();

                try (Watch<V1ConfigMap> watch = Watch.createWatch(client, call, responseType)) {
                    assertThat(watch.hasNext()).isTrue();
                    Watch.Response<V1ConfigMap> added = watch.next();
                    assertThat(added.type).isEqualTo("ADDED");
                    assertThat(added.object.getMetadata().getName()).isEqualTo("sync-settings");
                    assertThat(added.object.getMetadata().getNamespace()).isEqualTo("apps");
                    assertThat(added.object.getMetadata().getResourceVersion()).isEqualTo("41");
                    assertThat(added.object.getData()).containsEntry("mode", "active");

                    assertThat(watch.hasNext()).isTrue();
                    Watch.Response<V1ConfigMap> modified = watch.next();
                    assertThat(modified.type).isEqualTo("MODIFIED");
                    assertThat(modified.object.getMetadata().getResourceVersion()).isEqualTo("42");
                    assertThat(modified.object.getData()).containsEntry("mode", "passive");
                    assertThat(watch.hasNext()).isFalse();
                }

                RecordedRequest request = server.takeRequest(10, TimeUnit.SECONDS);
                assertThat(request).isNotNull();
                assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/api/v1/namespaces/apps/configmaps");
                assertThat(request.getRequestUrl().queryParameter("watch")).isEqualTo("true");
            } finally {
                httpClient.connectionPool().evictAll();
                httpClient.dispatcher().executorService().shutdownNow();
            }
        }
    }
}
