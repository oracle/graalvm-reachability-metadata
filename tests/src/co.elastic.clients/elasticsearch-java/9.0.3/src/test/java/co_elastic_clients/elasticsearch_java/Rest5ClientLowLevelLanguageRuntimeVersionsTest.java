/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.RequestOptions;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Rest5ClientLowLevelLanguageRuntimeVersionsTest {
    @Test
    void metadataHeaderIncludesDetectedJvmLanguageVersions() throws Exception {
        AtomicReference<String> metadataHeader = new AtomicReference<>();
        CapturingHttpAsyncClient httpClient = new CapturingHttpAsyncClient(metadataHeader);
        URI uri = URI.create("http://127.0.0.1:9200");

        try (Rest5Client client = Rest5Client.builder(uri).setHttpClient(httpClient).build()) {
            Request request = new Request("GET", "/");
            request.setOptions(requestOptionsWithBoundedTimeouts());

            Response response = client.performRequest(request);

            assertThat(response.getStatusCode()).isEqualTo(200);
        }

        assertThat(metadataHeader.get())
            .containsPattern("(^|,)kt=\\d+\\.\\d+(,|$)")
            .containsPattern("(^|,)sc=\\d+\\.\\d+(,|$)");
    }

    private static RequestOptions requestOptionsWithBoundedTimeouts() {
        Timeout timeout = Timeout.ofSeconds(10);
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(timeout)
            .setConnectTimeout(timeout)
            .setResponseTimeout(timeout)
            .build();
        return RequestOptions.DEFAULT.toBuilder()
            .setRequestConfig(requestConfig)
            .build();
    }

    private static final class CapturingHttpAsyncClient extends CloseableHttpAsyncClient {
        private final AtomicReference<String> metadataHeader;
        private IOReactorStatus status;

        private CapturingHttpAsyncClient(AtomicReference<String> metadataHeader) {
            this.metadataHeader = metadataHeader;
            this.status = IOReactorStatus.INACTIVE;
        }

        @Override
        public void start() {
            status = IOReactorStatus.ACTIVE;
        }

        @Override
        public IOReactorStatus getStatus() {
            return status;
        }

        @Override
        public void awaitShutdown(TimeValue waitTime) {
            // No-op: this test client does not manage background resources.
        }

        @Override
        public void initiateShutdown() {
            status = IOReactorStatus.SHUT_DOWN;
        }

        @Override
        public void close(CloseMode closeMode) {
            status = IOReactorStatus.SHUT_DOWN;
        }

        @Override
        public void close() {
            close(CloseMode.GRACEFUL);
        }

        @Override
        public void register(String hostname, String uriPattern, Supplier<AsyncPushConsumer> supplier) {
            // No-op: this test does not exercise server push.
        }

        @Override
        protected <T> Future<T> doExecute(
            HttpHost target,
            AsyncRequestProducer requestProducer,
            AsyncResponseConsumer<T> responseConsumer,
            HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            HttpContext context,
            FutureCallback<T> callback
        ) {
            BasicFuture<T> future = new BasicFuture<>(callback);
            try {
                requestProducer.sendRequest((request, entityDetails, requestContext) -> {
                    Header header = request.getFirstHeader("X-Elastic-Client-Meta");
                    metadataHeader.set(header == null ? null : header.getValue());
                }, context);
                BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
                response.setHeader("Content-Type", ContentType.APPLICATION_JSON.toString());
                response.setEntity(new StringEntity("{}", ContentType.APPLICATION_JSON));
                @SuppressWarnings("unchecked")
                T typedResponse = (T) response;
                future.completed(typedResponse);
            } catch (Exception exception) {
                future.failed(exception);
            } finally {
                requestProducer.releaseResources();
            }
            return future;
        }
    }
}
