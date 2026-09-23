/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.annotation.BodyParam;
import com.azure.core.annotation.ExpectedResponses;
import com.azure.core.annotation.Get;
import com.azure.core.annotation.HeaderParam;
import com.azure.core.annotation.Host;
import com.azure.core.annotation.PathParam;
import com.azure.core.annotation.Post;
import com.azure.core.annotation.ServiceInterface;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.AzureNamedKey;
import com.azure.core.credential.AzureNamedKeyCredential;
import com.azure.core.credential.AzureSasCredential;
import com.azure.core.credential.BasicAuthenticationCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.AddHeadersPolicy;
import com.azure.core.http.policy.RequestIdPolicy;
import com.azure.core.http.policy.RetryPolicy;
import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.http.rest.PagedResponseBase;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.RestProxy;
import com.azure.core.models.AzureCloud;
import com.azure.core.models.CloudEvent;
import com.azure.core.models.CloudEventDataFormat;
import com.azure.core.models.GeoPoint;
import com.azure.core.models.JsonPatchDocument;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.core.util.serializer.JacksonAdapter;
import com.azure.core.util.serializer.SerializerAdapter;
import com.azure.core.util.serializer.SerializerEncoding;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Timeout(60)
public class Azure_coreTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void restProxyBuildsAnnotatedRequestsAndMaterializesResponses() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                response(200, ""),
                response(201, ""));
        HttpPipeline pipeline = new HttpPipelineBuilder().httpClient(httpClient).build();
        WidgetService service = RestProxy.create(WidgetService.class, pipeline, new JacksonAdapter());

        Response<Void> fetched = service.getWidget("item with space", "trace-1").block(IO_TIMEOUT);
        Response<Void> created = service.createWidget(BinaryData.fromString("{\"name\":\"new widget\"}"), 21L)
                .block(IO_TIMEOUT);

        assertThat(fetched).isNotNull();
        assertThat(fetched.getStatusCode()).isEqualTo(200);
        assertThat(created).isNotNull();
        assertThat(created.getStatusCode()).isEqualTo(201);
        assertThat(httpClient.requests()).hasSize(2);

        HttpRequest getRequest = httpClient.requests().get(0);
        assertThat(getRequest.getHttpMethod()).isEqualTo(HttpMethod.GET);
        assertThat(getRequest.getUrl().toString()).isEqualTo("https://example.test/widgets/item%20with%20space");
        assertThat(getRequest.getHeaders().getValue("X-Trace-Id")).isEqualTo("trace-1");

        HttpRequest postRequest = httpClient.requests().get(1);
        assertThat(postRequest.getHttpMethod()).isEqualTo(HttpMethod.POST);
        assertThat(postRequest.getUrl().toString()).isEqualTo("https://example.test/widgets");
        assertThat(postRequest.getHeaders().getValue(HttpHeaderName.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(postRequest.getHeaders().getValue(HttpHeaderName.CONTENT_LENGTH)).isEqualTo("21");
        assertThat(postRequest.getBodyAsBinaryData().toString()).isEqualTo("{\"name\":\"new widget\"}");
    }

    @Test
    void pipelineAppliesPoliciesRetriesTransientResponseAndSupportsSyncCalls() throws Exception {
        HttpHeaders unavailableHeaders = new HttpHeaders().set(HttpHeaderName.RETRY_AFTER, "0");
        RecordingHttpClient httpClient = new RecordingHttpClient(
                response(503, unavailableHeaders, "try again"),
                response(200, "retried response"),
                response(202, "sync response"));
        HttpHeaders policyHeaders = new HttpHeaders().set("X-Application", "core-test");
        HttpPipeline pipeline = new HttpPipelineBuilder()
                .httpClient(httpClient)
                .policies(new AddHeadersPolicy(policyHeaders), new RequestIdPolicy(), new RetryPolicy())
                .build();

        HttpRequest asyncRequest = new HttpRequest(HttpMethod.GET, new URL("https://example.test/retry"));
        try (HttpResponse response = pipeline.send(asyncRequest, Context.NONE).block(IO_TIMEOUT)) {
            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getBodyAsString().block(IO_TIMEOUT)).isEqualTo("retried response");
        }

        HttpRequest syncRequest = new HttpRequest(HttpMethod.HEAD, new URL("https://example.test/sync"));
        try (HttpResponse response = pipeline.sendSync(syncRequest, Context.NONE)) {
            assertThat(response.getStatusCode()).isEqualTo(202);
        }

        assertThat(httpClient.requests()).hasSize(3);
        assertThat(httpClient.requests().subList(0, 2))
                .allSatisfy(request -> {
                    assertThat(request.getHeaders().getValue("X-Application")).isEqualTo("core-test");
                    assertThat(request.getHeaders().getValue("x-ms-client-request-id")).isNotBlank();
                });
        assertThat(httpClient.requests().get(2).getHttpMethod()).isEqualTo(HttpMethod.HEAD);
    }

    @Test
    void jacksonAdapterRoundTripsJsonSerializableCoreModels() throws Exception {
        SerializerAdapter serializer = new JacksonAdapter();
        GeoPoint originalPoint = new GeoPoint(12.5, -7.25, 100.0);

        String pointJson = serializer.serialize(originalPoint, SerializerEncoding.JSON);
        GeoPoint decodedPoint = serializer.deserialize(pointJson, GeoPoint.class, SerializerEncoding.JSON);

        assertThat(decodedPoint).isEqualTo(originalPoint);
        assertThat(decodedPoint.getCoordinates().getLongitude()).isEqualTo(12.5);
        assertThat(decodedPoint.getCoordinates().getLatitude()).isEqualTo(-7.25);
        assertThat(decodedPoint.getCoordinates().getAltitude()).isEqualTo(100.0);

        CloudEvent originalEvent = new CloudEvent(
                        "/integration/source",
                        "widget.created",
                        BinaryData.fromString("{\"name\":\"azure\",\"count\":2}"),
                        CloudEventDataFormat.JSON,
                        "application/json")
                .setId("event-42")
                .setSubject("widgets/42")
                .setTime(OffsetDateTime.parse("2024-05-01T10:15:30Z"))
                .addExtensionAttribute("region", "west");

        BinaryData encodedEvent = BinaryData.fromObject(originalEvent);
        CloudEvent decodedEvent = encodedEvent.toObject(CloudEvent.class);

        assertThat(decodedEvent.getId()).isEqualTo("event-42");
        assertThat(decodedEvent.getSource()).isEqualTo("/integration/source");
        assertThat(decodedEvent.getType()).isEqualTo("widget.created");
        assertThat(decodedEvent.getSubject()).isEqualTo("widgets/42");
        assertThat(decodedEvent.getTime()).isEqualTo(OffsetDateTime.parse("2024-05-01T10:15:30Z"));
        assertThat(decodedEvent.getExtensionAttributes()).containsEntry("region", "west");
        assertThat(decodedEvent.getData().toString()).isEqualTo("{\"name\":\"azure\",\"count\":2}");
    }

    @Test
    void jsonPatchDocumentSerializesAndDeserializesAllOperationShapes() throws Exception {
        SerializerAdapter serializer = JacksonAdapter.createDefaultSerializerAdapter();
        JsonPatchDocument patch = new JsonPatchDocument()
                .appendAddRaw("/enabled", "true")
                .appendReplaceRaw("/count", "8")
                .appendCopy("/name", "/displayName")
                .appendMove("/legacy", "/archive/legacy")
                .appendRemove("/obsolete")
                .appendTestRaw("/revision", "7");

        String json = serializer.serialize(patch, SerializerEncoding.JSON);
        JsonPatchDocument decoded = serializer.deserialize(json, JsonPatchDocument.class, SerializerEncoding.JSON);

        assertThat(json)
                .contains("\"op\":\"add\"", "\"op\":\"replace\"", "\"op\":\"copy\"")
                .contains("\"op\":\"move\"", "\"op\":\"remove\"", "\"op\":\"test\"")
                .contains("\"path\":\"/displayName\"", "\"value\":7");
        assertThat(decoded.toString()).isEqualTo(json);
    }

    @Test
    void binaryDataBuffersStreamsAndPreservesByteBufferBoundaries() throws Exception {
        ByteBuffer first = ByteBuffer.wrap("azure ".getBytes(UTF_8));
        ByteBuffer second = ByteBuffer.wrap("core".getBytes(UTF_8));
        BinaryData data = BinaryData.fromFlux(Flux.just(first, second), 10L, true).block(IO_TIMEOUT);

        assertThat(data).isNotNull();
        assertThat(data.getLength()).isEqualTo(10L);
        assertThat(data.isReplayable()).isTrue();
        assertThat(data.toString()).isEqualTo("azure core");
        assertThat(data.toBytes()).containsExactly("azure core".getBytes(UTF_8));
        assertThat(data.toStream().readAllBytes()).containsExactly("azure core".getBytes(UTF_8));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        data.writeTo(output);
        assertThat(output.toByteArray()).containsExactly("azure core".getBytes(UTF_8));
        assertThat(data.toFluxByteBuffer().map(ByteBuffer::remaining).collectList().block(IO_TIMEOUT))
                .containsExactly(6, 4);
    }

    @Test
    void pagedTypesTraverseContinuationTokensInAsyncAndSyncForms() throws Exception {
        HttpRequest request = new HttpRequest(HttpMethod.GET, new URL("https://example.test/items"));
        AtomicInteger asyncNextPageCalls = new AtomicInteger();
        PagedFlux<String> pagedFlux = new PagedFlux<>(
                () -> Mono.just(page(request, List.of("one", "two"), "page-2")),
                token -> {
                    asyncNextPageCalls.incrementAndGet();
                    assertThat(token).isEqualTo("page-2");
                    return Mono.just(page(request, List.of("three"), null));
                });

        assertThat(pagedFlux.collectList().block(IO_TIMEOUT)).containsExactly("one", "two", "three");
        assertThat(asyncNextPageCalls).hasValue(1);

        AtomicInteger syncNextPageCalls = new AtomicInteger();
        PagedIterable<String> pagedIterable = new PagedIterable<>(
                () -> page(request, List.of("alpha"), "next"),
                token -> {
                    syncNextPageCalls.incrementAndGet();
                    assertThat(token).isEqualTo("next");
                    return page(request, List.of("beta", "gamma"), null);
                });

        assertThat(pagedIterable.stream()).containsExactly("alpha", "beta", "gamma");
        assertThat(syncNextPageCalls).hasValue(1);
    }

    @Test
    void syncPollerCompletesLongRunningOperationAndFetchesResult() {
        AtomicInteger pollCalls = new AtomicInteger();
        SyncPoller<String, String> poller = SyncPoller.createPoller(
                Duration.ofMillis(1),
                context -> new PollResponse<>(LongRunningOperationStatus.NOT_STARTED, "accepted"),
                context -> pollCalls.incrementAndGet() == 1
                        ? new PollResponse<>(LongRunningOperationStatus.IN_PROGRESS, "working")
                        : new PollResponse<>(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED, "finished"),
                (context, response) -> "cancelled",
                context -> "result-for-" + context.getLatestResponse().getValue());

        PollResponse<String> finalResponse = poller.waitForCompletion(IO_TIMEOUT);

        assertThat(finalResponse.getStatus()).isEqualTo(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED);
        assertThat(finalResponse.getValue()).isEqualTo("finished");
        assertThat(poller.getFinalResult(IO_TIMEOUT)).isEqualTo("result-for-finished");
        assertThat(pollCalls).hasValue(2);
    }

    @Test
    void azureCloudFactorySupportsKnownAndCustomCloudNames() {
        AzureCloud publicCloud = AzureCloud.fromString("AZURE_PUBLIC_CLOUD");
        AzureCloud privateCloud = AzureCloud.fromString("CONTOSO_PRIVATE_CLOUD");

        assertThat(publicCloud).isSameAs(AzureCloud.AZURE_PUBLIC_CLOUD);
        assertThat(privateCloud.getValue()).isEqualTo("CONTOSO_PRIVATE_CLOUD");
        assertThat(AzureCloud.fromString("CONTOSO_PRIVATE_CLOUD")).isSameAs(privateCloud);
    }

    @Test
    void credentialsAndContextExposeUpdatesThroughPublicApis() {
        BasicAuthenticationCredential basic = new BasicAuthenticationCredential("azure-user", "secret");
        AccessToken basicToken = basic.getTokenSync(new TokenRequestContext().addScopes("scope/.default"));
        assertThat(basicToken.getToken()).isEqualTo("YXp1cmUtdXNlcjpzZWNyZXQ=");

        AzureNamedKeyCredential namedKey = new AzureNamedKeyCredential("first", "key-one");
        namedKey.update("second", "key-two");
        AzureNamedKey updated = namedKey.getAzureNamedKey();
        assertThat(updated.getName()).isEqualTo("second");
        assertThat(updated.getKey()).isEqualTo("key-two");

        AzureSasCredential sas = new AzureSasCredential("sig=old");
        sas.update("sig=new&se=tomorrow");
        assertThat(sas.getSignature()).isEqualTo("sig=new&se=tomorrow");

        Context context = Context.NONE.addData("request-id", "request-42").addData("attempt", 3);
        assertThat(context.getData("request-id")).contains("request-42");
        assertThat(context.getData("attempt")).contains(3);
    }

    private static PagedResponse<String> page(HttpRequest request, List<String> values, String continuationToken) {
        return new PagedResponseBase<>(request, 200, new HttpHeaders(), values, continuationToken, null);
    }

    private static ResponseSpec response(int statusCode, String body) {
        return response(statusCode, new HttpHeaders(), body);
    }

    private static ResponseSpec response(int statusCode, HttpHeaders headers, String body) {
        return new ResponseSpec(statusCode, headers, body);
    }

    @Host("https://example.test")
    @ServiceInterface(name = "WidgetService")
    public interface WidgetService {
        @Get("widgets/{id}")
        @ExpectedResponses({200})
        Mono<Response<Void>> getWidget(@PathParam("id") String id, @HeaderParam("X-Trace-Id") String traceId);

        @Post("widgets")
        @ExpectedResponses({201})
        Mono<Response<Void>> createWidget(
                @BodyParam("application/json") BinaryData body,
                @HeaderParam("Content-Length") Long contentLength);
    }

    private static final class RecordingHttpClient implements HttpClient {
        private final Queue<ResponseSpec> responses;
        private final List<HttpRequest> requests = new ArrayList<>();

        private RecordingHttpClient(ResponseSpec... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public Mono<HttpResponse> send(HttpRequest request) {
            requests.add(request.copy());
            ResponseSpec spec = responses.poll();
            if (spec == null) {
                return Mono.error(new IllegalStateException("No response configured for " + request.getUrl()));
            }
            return Mono.just(new CoreHttpResponse(request, spec));
        }

        private List<HttpRequest> requests() {
            return requests;
        }
    }

    private static final class CoreHttpResponse extends HttpResponse {
        private final int statusCode;
        private final HttpHeaders headers;
        private final byte[] body;

        private CoreHttpResponse(HttpRequest request, ResponseSpec response) {
            super(request);
            this.statusCode = response.statusCode();
            this.headers = response.headers();
            this.body = response.body().getBytes(UTF_8);
        }

        @Override
        public int getStatusCode() {
            return statusCode;
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
            return Flux.defer(() -> Flux.just(ByteBuffer.wrap(body)));
        }

        @Override
        public BinaryData getBodyAsBinaryData() {
            return BinaryData.fromBytes(body);
        }

        @Override
        public Mono<byte[]> getBodyAsByteArray() {
            return Mono.just(body.clone());
        }

        @Override
        public Mono<String> getBodyAsString() {
            return Mono.just(new String(body, UTF_8));
        }

        @Override
        public Mono<String> getBodyAsString(java.nio.charset.Charset charset) {
            return Mono.just(new String(body, charset));
        }
    }

    private record ResponseSpec(int statusCode, HttpHeaders headers, String body) {
    }

}
