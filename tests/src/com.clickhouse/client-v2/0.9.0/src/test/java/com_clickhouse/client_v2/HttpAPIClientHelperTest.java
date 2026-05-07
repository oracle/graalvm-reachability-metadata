/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.client_v2;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.clickhouse.client.api.ClientConfigProperties;
import com.clickhouse.client.api.internal.HttpAPIClientHelper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpAPIClientHelperTest {
    @Test
    void registersMicrometerBindersForPooledConnections() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpAPIClientHelper helper = new HttpAPIClientHelper(defaultConfiguration(), registry, false);
        try {
            assertThat(registry.find("httpcomponents.httpclient.pool.total.max").gauge()).isNotNull();
            assertThat(registry.find("httpcomponents.httpclient.connect.time").gauge()).isNotNull();
        } finally {
            helper.close();
            registry.close();
        }
    }

    @Test
    void buildsDefaultUserAgentForClientWithoutPackageImplementationMetadata() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(HttpAPIClientHelperTest.class.getClassLoader());
        try {
            HttpAPIClientHelper helper = new ResourceLoadingUserAgentHelper();
            helper.close();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    private static Map<String, String> defaultConfiguration() {
        Map<String, String> configuration = new HashMap<>();
        for (ClientConfigProperties property : ClientConfigProperties.values()) {
            if (property.getDefaultValue() != null) {
                configuration.put(property.getKey(), property.getDefaultValue());
            }
        }
        configuration.put(ClientConfigProperties.PASSWORD.getKey(), "");
        return configuration;
    }

    private static final class ResourceLoadingUserAgentHelper extends HttpAPIClientHelper {
        private ResourceLoadingUserAgentHelper() {
            super(defaultConfiguration(), null, false);
        }

        @Override
        public CloseableHttpClient createHttpClient(boolean initSslContext) {
            return new ResourceLoadingHttpClient();
        }
    }

    private static final class ResourceLoadingHttpClient extends CloseableHttpClient {
        @Override
        protected CloseableHttpResponse doExecute(HttpHost target, ClassicHttpRequest request, HttpContext context)
                throws IOException {
            throw new UnsupportedOperationException("HTTP execution is not used by this test");
        }

        @Override
        public void close(CloseMode closeMode) {
            Objects.requireNonNull(closeMode, "closeMode");
        }

        @Override
        public void close() {
            close(CloseMode.IMMEDIATE);
        }
    }
}
