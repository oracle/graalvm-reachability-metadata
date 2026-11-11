/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.concurrent.TimeUnit;

import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.binder.okhttp3.DefaultOkHttpObservationConvention;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpObservationInterceptor;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultOkHttpObservationConventionTest {

    @Test
    void observationRecordsHttpOutcomeAndTypedKeyValues() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (LocalHttpServer http = new LocalHttpServer()) {
            http.start();
            ObservationRegistry observations = ObservationRegistry.create();
            observations.observationConfig().observationHandler(new DefaultMeterObservationHandler(registry));
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(OkHttpObservationInterceptor.builder(observations, "observed.requests")
                            .observationConvention(new DefaultOkHttpObservationConvention("observed.requests"))
                            .uriMapper(request -> "/greeting").build())
                    .build();
            try {
                Request request = new Request.Builder().url(http.url())
                        .tag(KeyValues.class, KeyValues.of("operation", "greet")).build();
                try (Response response = client.newCall(request).execute()) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body().string()).isEqualTo("hello metrics");
                }
                assertThat(registry.get("observed.requests").tags("operation", "greet", "uri", "/greeting",
                        "status", "200", "outcome", "SUCCESS", "method", "GET").timer().count()).isEqualTo(1);
            } finally {
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdownNow();
            }
        } finally {
            registry.close();
        }
    }
}
