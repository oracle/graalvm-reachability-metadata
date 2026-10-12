/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_storage_blob;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.Response;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobImmutabilityPolicy;
import com.azure.storage.blob.models.BlobImmutabilityPolicyMode;
import com.azure.storage.common.StorageSharedKeyCredential;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Timeout(60)
public class BlobsSetImmutabilityPolicyHeadersTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);
    private static final String ACCOUNT_NAME = "integrationaccount";
    private static final String ENDPOINT = "https://" + ACCOUNT_NAME + ".blob.core.windows.net";
    private static final StorageSharedKeyCredential CREDENTIAL = new StorageSharedKeyCredential(
            ACCOUNT_NAME, Base64.getEncoder().encodeToString("integration-secret-key".getBytes(UTF_8)));

    @Test
    void setsTimeBasedImmutabilityPolicy() {
        OffsetDateTime expiryTime = OffsetDateTime.parse("2035-05-01T12:30:00Z");
        HttpHeaders responseHeaders = new HttpHeaders()
                .set("Content-Length", "0")
                .set("x-ms-request-id", "immutability-request")
                .set("x-ms-immutability-policy-mode", "Unlocked")
                .set("x-ms-immutability-policy-until-date", "Tue, 01 May 2035 12:30:00 GMT");
        RecordingHttpClient httpClient = new RecordingHttpClient(responseHeaders);
        BlobClient blobClient = new BlobServiceClientBuilder()
                .endpoint(ENDPOINT)
                .credential(CREDENTIAL)
                .httpClient(httpClient)
                .buildClient()
                .getBlobContainerClient("documents")
                .getBlobClient("archive.csv");
        BlobImmutabilityPolicy requestedPolicy = new BlobImmutabilityPolicy()
                .setPolicyMode(BlobImmutabilityPolicyMode.UNLOCKED)
                .setExpiryTime(expiryTime);

        Response<BlobImmutabilityPolicy> response = blobClient.setImmutabilityPolicyWithResponse(
                requestedPolicy, null, IO_TIMEOUT, Context.NONE);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue().getPolicyMode()).isEqualTo(BlobImmutabilityPolicyMode.UNLOCKED);
        assertThat(response.getValue().getExpiryTime()).isEqualTo(expiryTime);

        HttpRequest request = httpClient.request();
        assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.PUT);
        assertThat(URLDecoder.decode(request.getUrl().getPath(), UTF_8))
                .isEqualTo("/documents/archive.csv");
        assertThat(URLDecoder.decode(request.getUrl().getQuery(), UTF_8))
                .contains("comp=immutabilityPolicies");
        assertThat(request.getHeaders().getValue("x-ms-immutability-policy-mode"))
                .isEqualTo("Unlocked");
        assertThat(request.getHeaders().getValue("x-ms-immutability-policy-until-date"))
                .isEqualTo("Tue, 01 May 2035 12:30:00 GMT");
        assertThat(request.getHeaders().getValue("Authorization")).startsWith("SharedKey " + ACCOUNT_NAME + ":");
    }

    private static final class RecordingHttpClient implements HttpClient {
        private final HttpHeaders responseHeaders;
        private HttpRequest request;

        private RecordingHttpClient(HttpHeaders responseHeaders) {
            this.responseHeaders = responseHeaders;
        }

        @Override
        public Mono<HttpResponse> send(HttpRequest request) {
            this.request = request.copy();
            return Mono.just(new EmptyHttpResponse(request, responseHeaders));
        }

        private HttpRequest request() {
            return request;
        }
    }

    private static final class EmptyHttpResponse extends HttpResponse {
        private final HttpHeaders headers;

        private EmptyHttpResponse(HttpRequest request, HttpHeaders headers) {
            super(request);
            this.headers = headers;
        }

        @Override
        public int getStatusCode() {
            return 200;
        }

        @Override
        public String getHeaderValue(String name) {
            return headers.getValue(name);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Flux<ByteBuffer> getBody() {
            return Flux.empty();
        }

        @Override
        public BinaryData getBodyAsBinaryData() {
            return BinaryData.fromBytes(new byte[0]);
        }

        @Override
        public Mono<byte[]> getBodyAsByteArray() {
            return Mono.just(new byte[0]);
        }

        @Override
        public Mono<String> getBodyAsString() {
            return Mono.just("");
        }

        @Override
        public Mono<String> getBodyAsString(Charset charset) {
            return Mono.just("");
        }
    }
}
