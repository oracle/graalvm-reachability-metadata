/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api.gax_httpjson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.core.NanoClock;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.httpjson.ApiMethodDescriptor;
import com.google.api.gax.httpjson.GaxHttpJsonProperties;
import com.google.api.gax.httpjson.HttpHeadersUtils;
import com.google.api.gax.httpjson.HttpJsonCallContext;
import com.google.api.gax.httpjson.HttpJsonCallOptions;
import com.google.api.gax.httpjson.HttpJsonCallSettings;
import com.google.api.gax.httpjson.HttpJsonCallableFactory;
import com.google.api.gax.httpjson.HttpJsonHeaderEnhancers;
import com.google.api.gax.httpjson.HttpJsonOperationSnapshot;
import com.google.api.gax.httpjson.HttpJsonStatusCode;
import com.google.api.gax.httpjson.HttpJsonStatusRuntimeException;
import com.google.api.gax.httpjson.HttpJsonTransportChannel;
import com.google.api.gax.httpjson.InstantiatingHttpJsonChannelProvider;
import com.google.api.gax.httpjson.ManagedHttpJsonChannel;
import com.google.api.gax.httpjson.longrunning.OperationsSettings;
import com.google.api.gax.httpjson.ProtoMessageRequestFormatter;
import com.google.api.gax.httpjson.ProtoMessageResponseParser;
import com.google.api.gax.httpjson.ProtoOperationTransformers;
import com.google.api.gax.httpjson.ProtoRestSerializer;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.tracing.BaseApiTracerFactory;
import com.google.auth.ApiKeyCredentials;
import com.google.longrunning.ListOperationsRequest;
import com.google.longrunning.Operation;
import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.TypeRegistry;
import com.google.rpc.Code;
import com.google.rpc.Status;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Gax_httpjsonTest {
    @Test
    void protoRequestFormatterExpandsPathQueryParametersAndBody() {
        ProtoRestSerializer<ListOperationsRequest> serializer = ProtoRestSerializer.create();
        ProtoMessageRequestFormatter<ListOperationsRequest> formatter =
                ProtoMessageRequestFormatter.<ListOperationsRequest>newBuilder()
                        .setPath(
                                "/v1/{name=projects/*}/operations",
                                request -> {
                                    Map<String, String> pathParams = new HashMap<>();
                                    serializer.putPathParam(pathParams, "name", request.getName());
                                    return pathParams;
                                })
                        .setAdditionalPaths("/v1/{name=folders/*}/operations")
                        .setQueryParamsExtractor(
                                request -> {
                                    Map<String, List<String>> queryParams = new HashMap<>();
                                    serializer.putQueryParam(queryParams, "filter", request.getFilter());
                                    serializer.putQueryParam(queryParams, "pageSize", request.getPageSize());
                                    serializer.putQueryParam(queryParams, "pageToken", request.getPageToken());
                                    return queryParams;
                                })
                        .setRequestBodyExtractor(request -> serializer.toBody("*", request))
                        .build();

        ListOperationsRequest request = ListOperationsRequest.newBuilder()
                .setName("projects/sample")
                .setFilter("done = false")
                .setPageSize(25)
                .setPageToken("next-page")
                .build();

        assertThat(formatter.getPath(request)).isEqualTo("v1/projects/sample/operations");
        assertThat(formatter.getQueryParamNames(request))
                .containsEntry("filter", List.of("done = false"))
                .containsEntry("pageSize", List.of("25"))
                .containsEntry("pageToken", List.of("next-page"));
        assertThat(formatter.getRequestBody(request))
                .contains("projects/sample")
                .contains("done \\u003d false")
                .contains("next-page");
        assertThat(formatter.getPathTemplate().matches("v1/projects/sample/operations")).isTrue();
        assertThat(formatter.getAdditionalPathTemplates().get(0).matches("v1/folders/sample/operations")).isTrue();
    }

    @Test
    void protoResponseParserParsesReadersStreamsAndSerializesMessages() {
        ProtoMessageResponseParser<Operation> parser = ProtoMessageResponseParser.<Operation>newBuilder()
                .setDefaultInstance(Operation.getDefaultInstance())
                .build();
        Operation operation = Operation.newBuilder().setName("operations/round-trip").setDone(true).build();

        String json = parser.serialize(operation);
        Operation parsedFromStream = parser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        Operation parsedFromReader = parser.parse(new StringReader(json), TypeRegistry.getEmptyTypeRegistry());

        assertThat(json).contains("operations/round-trip").contains("done");
        assertThat(parsedFromStream).isEqualTo(operation);
        assertThat(parsedFromReader).isEqualTo(operation);
    }

    @Test
    void operationSnapshotExposesPackedResponseMetadataAndErrorStatus() {
        FieldMask metadata = FieldMask.newBuilder().addPaths("name").addPaths("done").build();
        Operation successfulOperation = Operation.newBuilder()
                .setName("operations/success")
                .setDone(true)
                .setResponse(Any.pack(Empty.getDefaultInstance()))
                .setMetadata(Any.pack(metadata))
                .build();

        OperationSnapshot snapshot = HttpJsonOperationSnapshot.create(successfulOperation);
        Empty response = ProtoOperationTransformers.ResponseTransformer.create(Empty.class).apply(snapshot);
        FieldMask unpackedMetadata =
                ProtoOperationTransformers.MetadataTransformer.create(FieldMask.class).apply(snapshot);

        assertThat(snapshot.getName()).isEqualTo("operations/success");
        assertThat(snapshot.isDone()).isTrue();
        assertThat(response).isEqualTo(Empty.getDefaultInstance());
        assertThat(unpackedMetadata).isEqualTo(metadata);

        Operation failedOperation = Operation.newBuilder()
                .setName("operations/failure")
                .setDone(true)
                .setError(Status.newBuilder()
                        .setCode(Code.NOT_FOUND.getNumber())
                        .setMessage("operation target was not found"))
                .build();
        OperationSnapshot failedSnapshot = HttpJsonOperationSnapshot.create(failedOperation);

        assertThat(failedSnapshot.getErrorCode().getCode()).isEqualTo(StatusCode.Code.NOT_FOUND);
        assertThat(failedSnapshot.getErrorCode().getTransportCode()).isEqualTo(404);
        assertThat(failedSnapshot.getErrorMessage()).isEqualTo("operation target was not found");
    }

    @Test
    void statusCodesHeadersAndPropertiesUseHttpJsonMappings() {
        assertThat(HttpJsonStatusCode.of(200).getCode()).isEqualTo(StatusCode.Code.OK);
        assertThat(HttpJsonStatusCode.of(404).getCode()).isEqualTo(StatusCode.Code.NOT_FOUND);
        assertThat(HttpJsonStatusCode.of(StatusCode.Code.UNAVAILABLE).getTransportCode()).isEqualTo(503);
        assertThat(HttpJsonStatusCode.of(Code.PERMISSION_DENIED).getCode())
                .isEqualTo(StatusCode.Code.PERMISSION_DENIED);
        assertThat(HttpJsonStatusCode.of(429).toString()).contains("RESOURCE_EXHAUSTED");

        HttpJsonStatusRuntimeException exception =
                new HttpJsonStatusRuntimeException(418, "short and stout", new IllegalStateException("cause"));
        assertThat(exception.getStatusCode()).isEqualTo(418);
        assertThat(exception).hasMessage("short and stout").hasCauseInstanceOf(IllegalStateException.class);

        Map<String, String> rawHeaders = new HashMap<>();
        rawHeaders.put("User-Agent", "custom-agent");
        rawHeaders.put("x-goog-api-client", "test-client");
        assertThat(HttpHeadersUtils.getUserAgentValue(rawHeaders)).isEqualTo("custom-agent");

        HttpHeaders headers = HttpHeadersUtils.setHeaders(new HttpHeaders(), rawHeaders);
        HttpHeadersUtils.setHeader(headers, "Content-Length", "123");
        HttpJsonHeaderEnhancers.create("x-extra", "extra-value").enhance(headers);

        assertThat(headers.getUserAgent()).isEqualTo("custom-agent");
        assertThat(headers.get("x-goog-api-client")).isEqualTo("test-client");
        assertThat(headers.getContentLength()).isEqualTo(123L);
        assertThat(headers.get("x-extra")).isEqualTo("extra-value");
        assertThat(GaxHttpJsonProperties.getHttpJsonTokenName()).isEqualTo("rest");
        assertThat(GaxHttpJsonProperties.getHttpJsonVersion()).isNotNull();
        assertThat(GaxHttpJsonProperties.getDefaultApiClientHeaderPattern()
                        .matcher("gl-java/21 gapic/test--protobuf-test gax/test rest/")
                        .matches())
                .isTrue();
    }

    @Test
    void callOptionsContextAndCallSettingsMergeTypedConfiguration() {
        TypeRegistry typeRegistry = TypeRegistry.newBuilder().add(Empty.getDescriptor()).build();
        ApiKeyCredentials credentials = ApiKeyCredentials.create("test-api-key");
        HttpJsonCallOptions baseOptions = HttpJsonCallOptions.newBuilder()
                .setTimeoutDuration(Duration.ofSeconds(10))
                .setCredentials(credentials)
                .setTypeRegistry(typeRegistry)
                .build();
        Instant deadline = Instant.now().plusSeconds(30);
        HttpJsonCallOptions overrideOptions = HttpJsonCallOptions.newBuilder()
                .setTimeoutDuration(Duration.ofSeconds(3))
                .setDeadlineInstant(deadline)
                .build();

        HttpJsonCallOptions mergedOptions = baseOptions.merge(overrideOptions);

        assertThat(mergedOptions.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(3));
        assertThat(mergedOptions.getDeadlineInstant()).isEqualTo(deadline);
        assertThat(mergedOptions.getCredentials()).isSameAs(credentials);
        assertThat(mergedOptions.getTypeRegistry()).isSameAs(typeRegistry);

        RetrySettings retrySettings = RetrySettings.newBuilder()
                .setMaxAttempts(2)
                .setTotalTimeoutDuration(Duration.ofSeconds(5))
                .setInitialRetryDelayDuration(Duration.ofMillis(10))
                .setRetryDelayMultiplier(1.0)
                .setMaxRetryDelayDuration(Duration.ofMillis(20))
                .setInitialRpcTimeoutDuration(Duration.ofSeconds(1))
                .setRpcTimeoutMultiplier(1.0)
                .setMaxRpcTimeoutDuration(Duration.ofSeconds(1))
                .build();
        HttpJsonCallContext baseContext = (HttpJsonCallContext) HttpJsonCallContext.createDefault()
                .withCallOptions(baseOptions)
                .withTimeoutDuration(Duration.ofSeconds(8))
                .withRetrySettings(retrySettings)
                .withRetryableCodes(Set.of(StatusCode.Code.UNAVAILABLE))
                .withExtraHeaders(Map.of("x-base", List.of("base")));
        HttpJsonCallContext overrideContext = (HttpJsonCallContext) HttpJsonCallContext.createDefault()
                .withCallOptions(overrideOptions)
                .withExtraHeaders(Map.of("x-override", List.of("override")));

        HttpJsonCallContext mergedContext = baseContext.merge(overrideContext);

        assertThat(mergedContext.getCallOptions().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(3));
        assertThat(mergedContext.getCallOptions().getDeadlineInstant()).isEqualTo(deadline);
        assertThat(mergedContext.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(8));
        assertThat(mergedContext.getExtraHeaders())
                .containsEntry("x-base", List.of("base"))
                .containsEntry("x-override", List.of("override"));
        assertThat(mergedContext.getRetrySettings()).isSameAs(retrySettings);
        assertThat(mergedContext.getRetryableCodes()).containsExactly(StatusCode.Code.UNAVAILABLE);

        ApiMethodDescriptor<ListOperationsRequest, Operation> descriptor =
                ApiMethodDescriptor.<ListOperationsRequest, Operation>newBuilder()
                        .setFullMethodName("google.longrunning.Operations/ListOperations")
                        .setHttpMethod("GET")
                        .setType(ApiMethodDescriptor.MethodType.UNARY)
                        .setRequestFormatter(minimalListOperationsFormatter())
                        .setResponseParser(ProtoMessageResponseParser.<Operation>newBuilder()
                                .setDefaultInstance(Operation.getDefaultInstance())
                                .build())
                        .build();
        HttpJsonCallSettings<ListOperationsRequest, Operation> settings =
                HttpJsonCallSettings.<ListOperationsRequest, Operation>newBuilder()
                        .setMethodDescriptor(descriptor)
                        .setTypeRegistry(typeRegistry)
                        .setParamsExtractor(request -> Map.of("name", request.getName()))
                        .build();

        assertThat(settings.getMethodDescriptor()).isSameAs(descriptor);
        assertThat(settings.getTypeRegistry()).isSameAs(typeRegistry);
        assertThat(settings.getParamsExtractor().extract(ListOperationsRequest.newBuilder()
                        .setName("projects/sample")
                        .build()))
                .containsEntry("name", "projects/sample");
    }

    @Test
    void channelProviderCreatesClosableHttpJsonTransportChannels() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            InstantiatingHttpJsonChannelProvider provider = InstantiatingHttpJsonChannelProvider.newBuilder()
                    .setEndpoint("localhost:8080")
                    .setExecutor(executor)
                    .build();

            assertThat(provider.needsHeaders()).isTrue();
            assertThat(provider.needsExecutor()).isFalse();
            assertThat(provider.needsEndpoint()).isFalse();
            assertThat(provider.acceptsPoolSize()).isFalse();
            assertThat(provider.getTransportName()).isEqualTo(HttpJsonTransportChannel.getHttpJsonTransportName());
            InstantiatingHttpJsonChannelProvider endpointProvider =
                    (InstantiatingHttpJsonChannelProvider) provider.withEndpoint("example.com:443");
            assertThat(endpointProvider.getEndpoint()).isEqualTo("example.com:443");
            assertThatThrownBy(() -> provider.withPoolSize(2)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> provider.withCredentials(ApiKeyCredentials.create("test-api-key")))
                    .isInstanceOf(UnsupportedOperationException.class);

            InstantiatingHttpJsonChannelProvider readyProvider =
                    (InstantiatingHttpJsonChannelProvider) provider.withHeaders(Map.of("x-goog-api-client", "test"));
            HttpJsonTransportChannel transportChannel = readyProvider.getTransportChannel();
            try {
                assertThat(transportChannel.getTransportName())
                        .isEqualTo(HttpJsonTransportChannel.getHttpJsonTransportName());
                assertThat(transportChannel.getEmptyCallContext()).isInstanceOf(HttpJsonCallContext.class);
                assertThat(transportChannel.isShutdown()).isFalse();
            } finally {
                transportChannel.close();
            }
            assertThat(transportChannel.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
            assertThat(transportChannel.isShutdown()).isTrue();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void unaryCallableExecutesPatchRequestWithHttpMethodOverride() throws Exception {
        ProtoRestSerializer<ListOperationsRequest> serializer = ProtoRestSerializer.create();
        ProtoMessageRequestFormatter<ListOperationsRequest> formatter =
                ProtoMessageRequestFormatter.<ListOperationsRequest>newBuilder()
                        .setPath(
                                "/v1/{name=projects/*}/operations",
                                request -> {
                                    Map<String, String> pathParams = new HashMap<>();
                                    serializer.putPathParam(pathParams, "name", request.getName());
                                    return pathParams;
                                })
                        .setQueryParamsExtractor(
                                request -> {
                                    Map<String, List<String>> queryParams = new HashMap<>();
                                    serializer.putQueryParam(queryParams, "filter", request.getFilter());
                                    serializer.putQueryParam(queryParams, "pageSize", request.getPageSize());
                                    return queryParams;
                                })
                        .setRequestBodyExtractor(request -> serializer.toBody("*", request))
                        .build();
        ProtoMessageResponseParser<Operation> parser = ProtoMessageResponseParser.<Operation>newBuilder()
                .setDefaultInstance(Operation.getDefaultInstance())
                .build();
        ApiMethodDescriptor<ListOperationsRequest, Operation> descriptor =
                ApiMethodDescriptor.<ListOperationsRequest, Operation>newBuilder()
                        .setFullMethodName("google.longrunning.Operations.UpdateOperation")
                        .setHttpMethod("PATCH")
                        .setType(ApiMethodDescriptor.MethodType.UNARY)
                        .setRequestFormatter(formatter)
                        .setResponseParser(parser)
                        .build();
        HttpJsonCallSettings<ListOperationsRequest, Operation> callSettings =
                HttpJsonCallSettings.<ListOperationsRequest, Operation>newBuilder()
                        .setMethodDescriptor(descriptor)
                        .build();
        Operation expectedOperation = Operation.newBuilder().setName("operations/patched").setDone(true).build();
        CapturingHttpTransport httpTransport =
                new CapturingHttpTransport("{\"name\":\"operations/patched\",\"done\":true}");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        ManagedHttpJsonChannel managedChannel = ManagedHttpJsonChannel.newBuilder()
                .setEndpoint("test.googleapis.com")
                .setExecutor(executor)
                .setHttpTransport(httpTransport)
                .build();
        HttpJsonTransportChannel transportChannel = HttpJsonTransportChannel.create(managedChannel);
        try {
            HttpJsonCallContext callContext = (HttpJsonCallContext) HttpJsonCallContext.createDefault()
                    .withTransportChannel(transportChannel)
                    .withExtraHeaders(Map.of("x-test-header", List.of("from-context")));
            ClientContext clientContext = ClientContext.newBuilder()
                    .setClock(NanoClock.getDefaultClock())
                    .setDefaultCallContext(callContext)
                    .setExecutor(executor)
                    .setTracerFactory(BaseApiTracerFactory.getInstance())
                    .setTransportChannel(transportChannel)
                    .build();
            UnaryCallSettings<ListOperationsRequest, Operation> unaryCallSettings =
                    UnaryCallSettings.<ListOperationsRequest, Operation>newUnaryCallSettingsBuilder()
                            .setSimpleTimeoutNoRetriesDuration(Duration.ofSeconds(5))
                            .build();
            UnaryCallable<ListOperationsRequest, Operation> callable =
                    HttpJsonCallableFactory.createUnaryCallable(callSettings, unaryCallSettings, clientContext);
            ListOperationsRequest request = ListOperationsRequest.newBuilder()
                    .setName("projects/sample")
                    .setFilter("done=false")
                    .setPageSize(5)
                    .build();

            Operation operation = callable.call(request);

            assertThat(operation).isEqualTo(expectedOperation);
            assertThat(httpTransport.getRequestMethod()).isEqualTo("POST");
            assertThat(httpTransport.getRequestUrl())
                    .startsWith("https://test.googleapis.com/v1/projects/sample/operations")
                    .contains("filter=done%3Dfalse")
                    .contains("pageSize=5");
            assertThat(httpTransport.getHeaderValues("X-HTTP-Method-Override")).containsExactly("PATCH");
            assertThat(httpTransport.getHeaderValues("x-test-header")).containsExactly("from-context");
            assertThat(httpTransport.getRequestBody())
                    .contains("projects/sample")
                    .contains("pageSize")
                    .contains("5");
        } finally {
            transportChannel.close();
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void managedChannelLifecycleDelegatesThroughTransportChannel() throws Exception {
        ManagedHttpJsonChannel managedChannel = ManagedHttpJsonChannel.newBuilder()
                .setEndpoint("localhost:8080")
                .setExecutor(Runnable::run)
                .build();
        HttpJsonTransportChannel transportChannel = HttpJsonTransportChannel.create(managedChannel);

        try {
            assertThat(transportChannel.getChannel()).isSameAs(managedChannel);
            assertThat(transportChannel.getManagedChannel()).isSameAs(managedChannel);
            assertThat(transportChannel.getEmptyCallContext().getChannel()).isNull();
            assertThat(transportChannel.isShutdown()).isFalse();
        } finally {
            transportChannel.shutdownNow();
        }

        assertThat(transportChannel.isShutdown()).isTrue();
        assertThat(transportChannel.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void operationsSettingsApplySharedRetryConfigurationToLongRunningOperationCalls() throws Exception {
        RetrySettings retrySettings = RetrySettings.newBuilder()
                .setMaxAttempts(3)
                .setTotalTimeoutDuration(Duration.ofSeconds(12))
                .setInitialRetryDelayDuration(Duration.ofMillis(25))
                .setRetryDelayMultiplier(1.5)
                .setMaxRetryDelayDuration(Duration.ofSeconds(1))
                .setInitialRpcTimeoutDuration(Duration.ofSeconds(2))
                .setRpcTimeoutMultiplier(1.0)
                .setMaxRpcTimeoutDuration(Duration.ofSeconds(2))
                .build();
        OperationsSettings.Builder builder = OperationsSettings.newBuilder();
        builder.setCredentialsProvider(NoCredentialsProvider.create());
        builder.applyToAllUnaryMethods(
                input -> {
                    input.setRetrySettings(retrySettings);
                    return null;
                });

        OperationsSettings settings = builder.build();

        assertRetrySettingsMatch(settings.listOperationsSettings().getRetrySettings(), retrySettings);
        assertRetrySettingsMatch(settings.getOperationSettings().getRetrySettings(), retrySettings);
        assertRetrySettingsMatch(settings.deleteOperationSettings().getRetrySettings(), retrySettings);
        assertRetrySettingsMatch(settings.cancelOperationSettings().getRetrySettings(), retrySettings);
        assertThat(settings.toBuilder().getStubSettingsBuilder()).isNotNull();
    }

    private static void assertRetrySettingsMatch(RetrySettings actual, RetrySettings expected) {
        assertThat(actual.getMaxAttempts()).isEqualTo(expected.getMaxAttempts());
        assertThat(actual.getTotalTimeoutDuration()).isEqualTo(expected.getTotalTimeoutDuration());
        assertThat(actual.getInitialRetryDelayDuration()).isEqualTo(expected.getInitialRetryDelayDuration());
        assertThat(actual.getMaxRetryDelayDuration()).isEqualTo(expected.getMaxRetryDelayDuration());
        assertThat(actual.getInitialRpcTimeoutDuration()).isEqualTo(expected.getInitialRpcTimeoutDuration());
        assertThat(actual.getMaxRpcTimeoutDuration()).isEqualTo(expected.getMaxRpcTimeoutDuration());
    }

    private static ProtoMessageRequestFormatter<ListOperationsRequest> minimalListOperationsFormatter() {
        ProtoRestSerializer<ListOperationsRequest> serializer = ProtoRestSerializer.create();
        return ProtoMessageRequestFormatter.<ListOperationsRequest>newBuilder()
                .setPath(
                        "/v1/{name=projects/*}/operations",
                        request -> {
                            Map<String, String> pathParams = new HashMap<>();
                            serializer.putPathParam(pathParams, "name", request.getName());
                            return pathParams;
                        })
                .setQueryParamsExtractor(request -> Map.of())
                .setRequestBodyExtractor(request -> "")
                .build();
    }

    private static final class CapturingHttpTransport extends HttpTransport {
        private final String responseBody;
        private String requestMethod;
        private String requestUrl;
        private String requestBody;
        private final Map<String, List<String>> requestHeaders = new HashMap<>();

        private CapturingHttpTransport(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        protected LowLevelHttpRequest buildRequest(String method, String url) {
            requestMethod = method;
            requestUrl = url;
            return new LowLevelHttpRequest() {
                @Override
                public void addHeader(String name, String value) {
                    requestHeaders.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
                }

                @Override
                public LowLevelHttpResponse execute() throws IOException {
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    if (getStreamingContent() != null) {
                        getStreamingContent().writeTo(content);
                    }
                    requestBody = content.toString(StandardCharsets.UTF_8);
                    return new JsonLowLevelHttpResponse(responseBody);
                }
            };
        }

        private String getRequestMethod() {
            return requestMethod;
        }

        private String getRequestUrl() {
            return requestUrl;
        }

        private List<String> getHeaderValues(String name) {
            for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return List.of();
        }

        private String getRequestBody() {
            return requestBody;
        }
    }

    private static final class JsonLowLevelHttpResponse extends LowLevelHttpResponse {
        private final byte[] content;

        private JsonLowLevelHttpResponse(String responseBody) {
            this.content = responseBody.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getContent() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public String getContentEncoding() {
            return null;
        }

        @Override
        public long getContentLength() {
            return content.length;
        }

        @Override
        public String getContentType() {
            return "application/json; charset=utf-8";
        }

        @Override
        public String getStatusLine() {
            return "HTTP/1.1 200 OK";
        }

        @Override
        public int getStatusCode() {
            return 200;
        }

        @Override
        public String getReasonPhrase() {
            return "OK";
        }

        @Override
        public int getHeaderCount() {
            return 0;
        }

        @Override
        public String getHeaderName(int index) {
            return null;
        }

        @Override
        public String getHeaderValue(int index) {
            return null;
        }
    }
}
