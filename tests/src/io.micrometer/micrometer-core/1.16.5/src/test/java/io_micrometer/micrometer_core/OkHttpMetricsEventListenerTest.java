/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpMetricsEventListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OkHttpMetricsEventListenerTest {

    @Test
    void recordsRequestTimingAndTypedRequestTags() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (LocalHttpServer http = new LocalHttpServer()) {
            http.start();
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(30, TimeUnit.SECONDS)
                    .eventListener(OkHttpMetricsEventListener.builder(registry, "client.requests").build())
                    .build();
            try {
                Request request = new Request.Builder().url(http.url())
                        .header(OkHttpMetricsEventListener.URI_PATTERN, "/greeting")
                        .tag(Tags.class, Tags.of("operation", "greet")).build();
                try (Response response = client.newCall(request).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo("hello metrics");
                }
                assertThat(registry.get("client.requests").tags("operation", "greet", "uri", "/greeting",
                        "status", "200", "outcome", "SUCCESS").timer().count()).isEqualTo(1);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        } finally {
            registry.close();
        }
    }
}
