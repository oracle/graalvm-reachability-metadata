/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_instrumentation_api_incubator;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Opentelemetry_instrumentation_api_incubatorTest {
    private static final AttributeKey<String> DB_SYSTEM = stringKey("db.system");
    private static final AttributeKey<String> DB_USER = stringKey("db.user");
    private static final AttributeKey<String> DB_NAME = stringKey("db.name");
    private static final AttributeKey<String> DB_CONNECTION_STRING = stringKey("db.connection_string");
    private static final AttributeKey<String> DB_STATEMENT = stringKey("db.statement");
    private static final AttributeKey<String> DB_OPERATION = stringKey("db.operation");
    private static final AttributeKey<String> CODE_NAMESPACE = stringKey("code.namespace");
    private static final AttributeKey<String> CODE_FUNCTION = stringKey("code.function");
    private static final AttributeKey<Long> HTTP_REQUEST_BODY_SIZE = longKey("http.request.body.size");
    private static final AttributeKey<Long> HTTP_RESPONSE_BODY_SIZE = longKey("http.response.body.size");
    private static final AttributeKey<String> MESSAGING_SYSTEM = stringKey("messaging.system");
    private static final AttributeKey<String> MESSAGING_DESTINATION_NAME = stringKey("messaging.destination.name");
    private static final AttributeKey<String> MESSAGING_DESTINATION_TEMPLATE =
            stringKey("messaging.destination.template");
    private static final AttributeKey<Boolean> MESSAGING_DESTINATION_TEMPORARY =
            booleanKey("messaging.destination.temporary");
    private static final AttributeKey<Boolean> MESSAGING_DESTINATION_ANONYMOUS =
            booleanKey("messaging.destination.anonymous");
    private static final AttributeKey<String> MESSAGING_MESSAGE_CONVERSATION_ID =
            stringKey("messaging.message.conversation_id");
    private static final AttributeKey<Long> MESSAGING_MESSAGE_BODY_SIZE =
            longKey("messaging.message.body.size");
    private static final AttributeKey<Long> MESSAGING_MESSAGE_ENVELOPE_SIZE =
            longKey("messaging.message.envelope.size");
    private static final AttributeKey<String> MESSAGING_CLIENT_ID = stringKey("messaging.client_id");
    private static final AttributeKey<String> MESSAGING_OPERATION = stringKey("messaging.operation");
    private static final AttributeKey<String> MESSAGING_MESSAGE_ID = stringKey("messaging.message.id");
    private static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
            longKey("messaging.batch.message_count");
    private static final AttributeKey<String> PEER_SERVICE = stringKey("peer.service");
    private static final AttributeKey<String> RPC_SYSTEM = stringKey("rpc.system");
    private static final AttributeKey<String> RPC_SERVICE = stringKey("rpc.service");
    private static final AttributeKey<String> RPC_METHOD = stringKey("rpc.method");
    private static final AttributeKey<String> POOL_NAME = stringKey("pool.name");
    private static final AttributeKey<String> CONNECTION_STATE = stringKey("state");

    @Test
    void redisCommandSanitizerMasksOnlySensitiveArguments() {
        RedisCommandSanitizer sanitizer = RedisCommandSanitizer.create(true);

        assertThat(sanitizer.sanitize("AUTH", Collections.singletonList("password"))).isEqualTo("AUTH ?");
        assertThat(sanitizer.sanitize("HMSET", Arrays.asList("profile", "email", "a@example.com", "age", 42)))
                .isEqualTo("HMSET profile email ? age ?");
        assertThat(
                        sanitizer.sanitize(
                                "EVAL",
                                Arrays.asList(
                                        "return redis.call('get', KEYS[1])", "2", "key1", "key2", "secret")))
                .isEqualTo("EVAL return redis.call('get', KEYS[1]) 2 key1 key2 ?");
        assertThat(RedisCommandSanitizer.create(false).sanitize("AUTH", Collections.singletonList("password")))
                .isEqualTo("AUTH password");
    }

    @Test
    void databaseAttributeExtractorPopulatesGenericAttributes() {
        DatabaseRequest request =
                new DatabaseRequest(
                        "postgresql",
                        "app_user",
                        "inventory",
                        "postgresql://db.example:5432/inventory",
                        "UPDATE products SET price = 19.99 WHERE sku = 'ABC-123'",
                        "UPDATE");
        Attributes genericAttributes =
                extractOnStart(DbClientAttributesExtractor.create(new DatabaseGetter()), request);

        assertThat(genericAttributes.get(DB_SYSTEM)).isEqualTo("postgresql");
        assertThat(genericAttributes.get(DB_USER)).isEqualTo("app_user");
        assertThat(genericAttributes.get(DB_NAME)).isEqualTo("inventory");
        assertThat(genericAttributes.get(DB_CONNECTION_STRING))
                .isEqualTo("postgresql://db.example:5432/inventory");
        assertThat(genericAttributes.get(DB_STATEMENT)).contains("ABC-123");
        assertThat(genericAttributes.get(DB_OPERATION)).isEqualTo("UPDATE");
    }

    @Test
    void codeUtilitiesExtractAttributes() {
        ClassAndMethod classAndMethod =
                ClassAndMethod.create(Opentelemetry_instrumentation_api_incubatorTest.class, "handle");
        Attributes attributes =
                extractOnStart(
                        CodeAttributesExtractor.create(ClassAndMethod.codeAttributesGetter()), classAndMethod);

        assertThat(attributes.get(CODE_NAMESPACE))
                .isEqualTo(Opentelemetry_instrumentation_api_incubatorTest.class.getName());
        assertThat(attributes.get(CODE_FUNCTION)).isEqualTo("handle");
    }

    @Test
    void codeSpanNameExtractorUsesSimpleClassAndMethodNames() {
        CodeRequest request = new CodeRequest(Opentelemetry_instrumentation_api_incubatorTest.class, "handle");
        CodeRequest classOnlyRequest = new CodeRequest(NestedCode.class, null);
        CodeRequest unknownRequest = new CodeRequest(null, "handle");

        assertThat(CodeSpanNameExtractor.create(new CodeGetter()).extract(request))
                .isEqualTo("Opentelemetry_instrumentation_api_incubatorTest.handle");
        assertThat(CodeSpanNameExtractor.create(new CodeGetter()).extract(classOnlyRequest))
                .isEqualTo("NestedCode");
        assertThat(CodeSpanNameExtractor.create(new CodeGetter()).extract(unknownRequest))
                .isEqualTo("<unknown>.handle");
    }

    @Test
    void databaseSpanNameExtractorUsesOperationAndFallbackNames() {
        DatabaseGetter getter = new DatabaseGetter();
        DatabaseRequest request =
                new DatabaseRequest(
                        "postgresql",
                        "app_user",
                        "inventory",
                        "postgresql://db.example:5432/inventory",
                        "UPDATE products SET price = 19.99 WHERE sku = 'ABC-123'",
                        "UPDATE");
        DatabaseRequest fallbackRequest = new DatabaseRequest("postgresql", null, null, null, null, null);

        assertThat(DbClientSpanNameExtractor.create(getter).extract(request)).isEqualTo("UPDATE inventory");
        assertThat(DbClientSpanNameExtractor.create(getter).extract(fallbackRequest)).isEqualTo("DB Query");
    }

    @Test
    void messagingExtractorCapturesCoreAttributesAndConfiguredHeaders() {
        MessagingRequest request = MessagingRequest.normal();
        AttributesExtractor<MessagingRequest, MessagingResponse> extractor =
                MessagingAttributesExtractor.builder(new MessagingGetter(), MessageOperation.PUBLISH)
                        .setCapturedHeaders(Arrays.asList("TraceParent", "X-Request-Id"))
                        .build();
        Attributes attributes = extractOnStartAndEnd(extractor, request, new MessagingResponse("message-9", 3L));

        assertThat(attributes.get(MESSAGING_SYSTEM)).isEqualTo("kafka");
        assertThat(attributes.get(MESSAGING_DESTINATION_NAME)).isEqualTo("orders");
        assertThat(attributes.get(MESSAGING_DESTINATION_TEMPLATE)).isEqualTo("orders-{region}");
        assertThat(attributes.get(MESSAGING_DESTINATION_TEMPORARY)).isNull();
        assertThat(attributes.get(MESSAGING_DESTINATION_ANONYMOUS)).isNull();
        assertThat(attributes.get(MESSAGING_MESSAGE_CONVERSATION_ID)).isEqualTo("conversation-1");
        assertThat(attributes.get(MESSAGING_MESSAGE_BODY_SIZE)).isEqualTo(128L);
        assertThat(attributes.get(MESSAGING_MESSAGE_ENVELOPE_SIZE)).isEqualTo(256L);
        assertThat(attributes.get(MESSAGING_CLIENT_ID)).isEqualTo("client-a");
        assertThat(attributes.get(MESSAGING_OPERATION)).isEqualTo("publish");
        assertThat(attributes.get(MESSAGING_MESSAGE_ID)).isEqualTo("message-9");
        assertThat(attributes.get(MESSAGING_BATCH_MESSAGE_COUNT)).isEqualTo(3L);
        assertThat(attributes.get(stringArrayKey("messaging.header.traceparent"))).containsExactly("00-abcd");
        assertThat(attributes.get(stringArrayKey("messaging.header.x_request_id")))
                .containsExactly("request-1", "request-2");
    }

    @Test
    void messagingSpanNameAndTemporaryDestinationUseSemanticFallbacks() {
        MessagingGetter getter = new MessagingGetter();

        assertThat(
                        MessagingSpanNameExtractor.create(getter, MessageOperation.PROCESS)
                                .extract(MessagingRequest.normal()))
                .isEqualTo("orders process");
        assertThat(
                        MessagingSpanNameExtractor.create(getter, MessageOperation.RECEIVE)
                                .extract(MessagingRequest.temporary()))
                .isEqualTo("(temporary) receive");

        Attributes temporaryAttributes =
                extractOnStart(
                        MessagingAttributesExtractor.create(getter, MessageOperation.RECEIVE),
                        MessagingRequest.temporary());
        assertThat(temporaryAttributes.get(MESSAGING_DESTINATION_TEMPORARY)).isTrue();
        assertThat(temporaryAttributes.get(MESSAGING_DESTINATION_NAME)).isEqualTo("(temporary)");
        assertThat(temporaryAttributes.get(MESSAGING_DESTINATION_TEMPLATE)).isNull();
    }

    @Test
    void httpExperimentalExtractorParsesBodySizeHeaders() {
        HttpRequest request = new HttpRequest("example.com", 443, "https://example.com/api/orders/1", "25");
        HttpResponse response = new HttpResponse("512");
        AttributesExtractor<HttpRequest, HttpResponse> extractor =
                HttpExperimentalAttributesExtractor.create(new HttpGetter());

        Attributes attributes = extractOnStartAndEnd(extractor, request, response);
        Attributes invalidAttributes =
                extractOnStartAndEnd(
                        extractor,
                        new HttpRequest("example.com", 443, "https://example.com/api", "NaN"),
                        response);

        assertThat(attributes.get(HTTP_REQUEST_BODY_SIZE)).isEqualTo(25L);
        assertThat(attributes.get(HTTP_RESPONSE_BODY_SIZE)).isEqualTo(512L);
        assertThat(invalidAttributes.get(HTTP_REQUEST_BODY_SIZE)).isNull();
        assertThat(invalidAttributes.get(HTTP_RESPONSE_BODY_SIZE)).isEqualTo(512L);
    }

    @Test
    void peerServiceExtractorsResolveHostPortAndPathMappings() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("example.com", "generic-example");
        mapping.put("example.com:443/api", "public-api");
        mapping.put("example.com:443/api/orders", "orders-api");
        PeerServiceResolver resolver = PeerServiceResolver.create(mapping);
        HttpRequest request = new HttpRequest("example.com", 443, "https://example.com/api/orders/1", "0");

        Attributes httpAttributes =
                extractOnStartAndEnd(
                        HttpClientPeerServiceAttributesExtractor.create(new HttpGetter(), resolver),
                        request,
                        new HttpResponse("0"));
        Attributes networkAttributes =
                extractOnStartAndEnd(
                        PeerServiceAttributesExtractor.create(new ServerGetter(), resolver),
                        request,
                        new HttpResponse("0"));

        assertThat(resolver.isEmpty()).isFalse();
        assertThat(resolver.resolveService("example.com", 443, () -> "/api/orders/1")).isEqualTo("orders-api");
        assertThat(resolver.resolveService("example.com", 443, () -> "/unknown")).isEqualTo("generic-example");
        assertThat(httpAttributes.get(PEER_SERVICE)).isEqualTo("orders-api");
        assertThat(networkAttributes.get(PEER_SERVICE)).isEqualTo("generic-example");
        assertThat(PeerServiceResolver.create(Collections.<String, String>emptyMap()).isEmpty()).isTrue();
    }

    @Test
    void rpcExtractorsPopulateAttributesAndSpanNames() {
        RpcRequest request = new RpcRequest("grpc", "cart.CartService", "GetCart");
        RpcRequest incompleteRequest = new RpcRequest("grpc", "cart.CartService", null);

        Attributes clientAttributes = extractOnStart(RpcClientAttributesExtractor.create(new RpcGetter()), request);
        Attributes serverAttributes = extractOnStart(RpcServerAttributesExtractor.create(new RpcGetter()), request);

        assertThat(clientAttributes.get(RPC_SYSTEM)).isEqualTo("grpc");
        assertThat(clientAttributes.get(RPC_SERVICE)).isEqualTo("cart.CartService");
        assertThat(clientAttributes.get(RPC_METHOD)).isEqualTo("GetCart");
        assertThat(serverAttributes).isEqualTo(clientAttributes);
        assertThat(RpcSpanNameExtractor.create(new RpcGetter()).extract(request)).isEqualTo("cart.CartService/GetCart");
        assertThat(RpcSpanNameExtractor.create(new RpcGetter()).extract(incompleteRequest)).isEqualTo("RPC request");
    }

    @Test
    void connectionPoolMetricsCreateNoopInstrumentsAndAttachPoolAttributes() {
        DbConnectionPoolMetrics metrics =
                DbConnectionPoolMetrics.create(OpenTelemetry.noop(), "test-instrumentation", "orders-pool");
        ObservableLongMeasurement connections = metrics.connections();

        assertThat(connections).isNotNull();
        assertThat(metrics.minIdleConnections()).isNotNull();
        assertThat(metrics.maxIdleConnections()).isNotNull();
        assertThat(metrics.maxConnections()).isNotNull();
        assertThat(metrics.pendingRequestsForConnection()).isNotNull();
        assertThat(metrics.connectionTimeouts()).isNotNull();
        assertThat(metrics.connectionCreateTime()).isNotNull();
        assertThat(metrics.connectionWaitTime()).isNotNull();
        assertThat(metrics.connectionUseTime()).isNotNull();
        assertThat(metrics.getAttributes().get(POOL_NAME)).isEqualTo("orders-pool");
        assertThat(metrics.getUsedConnectionsAttributes().get(CONNECTION_STATE)).isEqualTo("used");
        assertThat(metrics.getIdleConnectionsAttributes().get(CONNECTION_STATE)).isEqualTo("idle");
    }

    @Test
    void loggingContextConstantsExposeStableMdcKeys() {
        assertThat(LoggingContextConstants.TRACE_ID).isEqualTo("trace_id");
        assertThat(LoggingContextConstants.SPAN_ID).isEqualTo("span_id");
        assertThat(LoggingContextConstants.TRACE_FLAGS).isEqualTo("trace_flags");
    }

    private static <REQUEST, RESPONSE> Attributes extractOnStart(
            AttributesExtractor<REQUEST, RESPONSE> extractor, REQUEST request) {
        AttributesBuilder attributes = Attributes.builder();
        extractor.onStart(attributes, Context.root(), request);
        return attributes.build();
    }

    private static <REQUEST, RESPONSE> Attributes extractOnStartAndEnd(
            AttributesExtractor<REQUEST, RESPONSE> extractor, REQUEST request, RESPONSE response) {
        AttributesBuilder attributes = Attributes.builder();
        extractor.onStart(attributes, Context.root(), request);
        extractor.onEnd(attributes, Context.root(), request, response, null);
        return attributes.build();
    }

    private static final class DatabaseRequest {
        private final String system;
        private final String user;
        private final String name;
        private final String connectionString;
        private final String statement;
        private final String operation;

        private DatabaseRequest(
                String system, String user, String name, String connectionString, String statement, String operation) {
            this.system = system;
            this.user = user;
            this.name = name;
            this.connectionString = connectionString;
            this.statement = statement;
            this.operation = operation;
        }
    }

    private static class CommonDatabaseGetter {
        public String getSystem(DatabaseRequest request) {
            return request.system;
        }

        public String getUser(DatabaseRequest request) {
            return request.user;
        }

        public String getName(DatabaseRequest request) {
            return request.name;
        }

        public String getConnectionString(DatabaseRequest request) {
            return request.connectionString;
        }
    }

    private static final class DatabaseGetter extends CommonDatabaseGetter
            implements DbClientAttributesGetter<DatabaseRequest> {
        @Override
        public String getStatement(DatabaseRequest request) {
            return request.statement;
        }

        @Override
        public String getOperation(DatabaseRequest request) {
            return request.operation;
        }
    }

    private static final class CodeRequest {
        private final Class<?> codeClass;
        private final String methodName;

        private CodeRequest(Class<?> codeClass, String methodName) {
            this.codeClass = codeClass;
            this.methodName = methodName;
        }
    }

    private static final class CodeGetter implements CodeAttributesGetter<CodeRequest> {
        @Override
        public Class<?> getCodeClass(CodeRequest request) {
            return request.codeClass;
        }

        @Override
        public String getMethodName(CodeRequest request) {
            return request.methodName;
        }
    }

    private static final class NestedCode {
    }

    private static final class MessagingRequest {
        private final boolean temporary;
        private final Map<String, List<String>> headers;

        private MessagingRequest(boolean temporary, Map<String, List<String>> headers) {
            this.temporary = temporary;
            this.headers = headers;
        }

        private static MessagingRequest normal() {
            Map<String, List<String>> headers = new HashMap<>();
            headers.put("traceparent", Collections.singletonList("00-abcd"));
            headers.put("x-request-id", Arrays.asList("request-1", "request-2"));
            return new MessagingRequest(false, headers);
        }

        private static MessagingRequest temporary() {
            return new MessagingRequest(true, Collections.emptyMap());
        }
    }

    private static final class MessagingResponse {
        private final String messageId;
        private final Long batchCount;

        private MessagingResponse(String messageId, Long batchCount) {
            this.messageId = messageId;
            this.batchCount = batchCount;
        }
    }

    private static final class MessagingGetter
            implements MessagingAttributesGetter<MessagingRequest, MessagingResponse> {
        @Override
        public String getSystem(MessagingRequest request) {
            return "kafka";
        }

        @Override
        public String getDestination(MessagingRequest request) {
            return request.temporary ? null : "orders";
        }

        @Override
        public String getDestinationTemplate(MessagingRequest request) {
            return request.temporary ? null : "orders-{region}";
        }

        @Override
        public boolean isTemporaryDestination(MessagingRequest request) {
            return request.temporary;
        }

        @Override
        public boolean isAnonymousDestination(MessagingRequest request) {
            return false;
        }

        @Override
        public String getConversationId(MessagingRequest request) {
            return "conversation-1";
        }

        @Override
        public Long getMessageBodySize(MessagingRequest request) {
            return 128L;
        }

        @Override
        public Long getMessageEnvelopeSize(MessagingRequest request) {
            return 256L;
        }

        @Override
        public String getMessageId(MessagingRequest request, MessagingResponse response) {
            return response == null ? null : response.messageId;
        }

        @Override
        public String getClientId(MessagingRequest request) {
            return "client-a";
        }

        @Override
        public Long getBatchMessageCount(MessagingRequest request, MessagingResponse response) {
            return response == null ? null : response.batchCount;
        }

        @Override
        public List<String> getMessageHeader(MessagingRequest request, String name) {
            return request.headers.getOrDefault(name.toLowerCase(Locale.ROOT), Collections.emptyList());
        }
    }

    private static final class HttpRequest {
        private final String serverAddress;
        private final Integer serverPort;
        private final String url;
        private final String requestContentLength;

        private HttpRequest(String serverAddress, Integer serverPort, String url, String requestContentLength) {
            this.serverAddress = serverAddress;
            this.serverPort = serverPort;
            this.url = url;
            this.requestContentLength = requestContentLength;
        }
    }

    private static final class HttpResponse {
        private final String responseContentLength;

        private HttpResponse(String responseContentLength) {
            this.responseContentLength = responseContentLength;
        }
    }

    private static final class HttpGetter implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {
        @Override
        public String getUrlFull(HttpRequest request) {
            return request.url;
        }

        @Override
        public String getServerAddress(HttpRequest request) {
            return request.serverAddress;
        }

        @Override
        public Integer getServerPort(HttpRequest request) {
            return request.serverPort;
        }

        @Override
        public String getHttpRequestMethod(HttpRequest request) {
            return "POST";
        }

        @Override
        public List<String> getHttpRequestHeader(HttpRequest request, String name) {
            if ("content-length".equals(name)) {
                return Collections.singletonList(request.requestContentLength);
            }
            return Collections.emptyList();
        }

        @Override
        public Integer getHttpResponseStatusCode(HttpRequest request, HttpResponse response, Throwable error) {
            return 200;
        }

        @Override
        public List<String> getHttpResponseHeader(HttpRequest request, HttpResponse response, String name) {
            if ("content-length".equals(name)) {
                return Collections.singletonList(response.responseContentLength);
            }
            return Collections.emptyList();
        }
    }

    private static final class ServerGetter implements ServerAttributesGetter<HttpRequest> {
        @Override
        public String getServerAddress(HttpRequest request) {
            return request.serverAddress;
        }

        @Override
        public Integer getServerPort(HttpRequest request) {
            return request.serverPort;
        }
    }

    private static final class RpcRequest {
        private final String system;
        private final String service;
        private final String method;

        private RpcRequest(String system, String service, String method) {
            this.system = system;
            this.service = service;
            this.method = method;
        }
    }

    private static final class RpcGetter implements RpcAttributesGetter<RpcRequest> {
        @Override
        public String getSystem(RpcRequest request) {
            return request.system;
        }

        @Override
        public String getService(RpcRequest request) {
            return request.service;
        }

        @Override
        public String getMethod(RpcRequest request) {
            return request.method;
        }
    }
}
