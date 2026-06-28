/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.client_v2;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ClientConfigProperties;
import com.clickhouse.client.api.internal.HttpAPIClientHelper;
import com.clickhouse.client.api.transport.HttpEndpoint;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jpountz.lz4.LZ4Factory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpAPIClientHelperTest {
    @Test
    void registersConnectionPoolMetricsWhenClientIsBuilt() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        try (Client client = new Client.Builder()
                .addEndpoint("http://localhost:8123")
                .setUsername("default")
                .setPassword("test")
                .registerClientMetrics(registry, "test-client")
                .build()) {
            assertThat(client).isNotNull();
            assertThat(registry.getMeters())
                    .extracting(Meter::getId)
                    .extracting(Meter.Id::getName)
                    .contains(
                            "httpcomponents.httpclient.pool.total.max",
                            "httpcomponents.httpclient.pool.total.connections",
                            "httpcomponents.httpclient.pool.total.pending",
                            "httpcomponents.httpclient.pool.route.max.default",
                            "httpcomponents.httpclient.connect.time");
        }
    }

    @Test
    void usesBundledHttpClientVersionResourceForShadedUserAgent() throws IOException {
        ResourceVersionHelper helper = new ResourceVersionHelper(configurationWithDefaults());

        try {
            String userAgent = helper.userAgentForRequest();

            assertThat(userAgent)
                    .contains("Apache-HttpClient/")
                    .doesNotContain("Apache-HttpClient/unknown");
        } finally {
            helper.close();
            assertThat(helper.isClosed()).isTrue();
        }
    }

    private static Map<String, String> configurationWithDefaults() {
        Map<String, String> configuration = new HashMap<>();
        for (ClientConfigProperties property : ClientConfigProperties.values()) {
            if (property.getDefaultValue() != null) {
                configuration.put(property.getKey(), property.getDefaultValue());
            }
        }
        configuration.put(ClientConfigProperties.PASSWORD.getKey(), "test");
        configuration.put(ClientConfigProperties.COMPRESS_SERVER_RESPONSE.getKey(), "false");
        return configuration;
    }

    private static final class ResourceVersionHelper extends HttpAPIClientHelper {
        private RecordingHttpClient recordingClient;

        private ResourceVersionHelper(Map<String, String> configuration) {
            super(configuration, null, false);
        }

        @Override
        public CloseableHttpClient createHttpClient(boolean initSslContext) {
            this.recordingClient = new RecordingHttpClient();
            return this.recordingClient;
        }

        private String userAgentForRequest() throws IOException {
            try (ClassicHttpResponse ignored = executeRequest(
                    new HttpEndpoint("http://localhost:8123"),
                    Map.of(),
                    LZ4Factory.fastestJavaInstance(),
                    out -> out.flush())) {
                return this.recordingClient.userAgent;
            }
        }

        private boolean isClosed() {
            return this.recordingClient.closed;
        }
    }

    private static final class RecordingHttpClient extends CloseableHttpClient {
        private String userAgent;
        private boolean closed;

        @Override
        public ClassicHttpResponse executeOpen(HttpHost target, ClassicHttpRequest request, HttpContext context) {
            Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
            this.userAgent = header == null ? "" : header.getValue();
            return new BasicClassicHttpResponse(HttpStatus.SC_OK);
        }

        @Override
        protected CloseableHttpResponse doExecute(HttpHost target, ClassicHttpRequest request, HttpContext context) {
            throw new UnsupportedOperationException("executeOpen is used by HttpAPIClientHelper");
        }

        @Override
        public void close() {
            close(CloseMode.GRACEFUL);
        }

        @Override
        public void close(CloseMode closeMode) {
            this.closed = true;
        }
    }
}
