/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_semconv.opentelemetry_semconv;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.AttributeKeyTemplate;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.ErrorAttributes.ErrorTypeValues;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.HttpAttributes.HttpRequestMethodValues;
import io.opentelemetry.semconv.JvmAttributes;
import io.opentelemetry.semconv.JvmAttributes.JvmMemoryTypeValues;
import io.opentelemetry.semconv.JvmAttributes.JvmThreadStateValues;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.NetworkAttributes.NetworkTransportValues;
import io.opentelemetry.semconv.NetworkAttributes.NetworkTypeValues;
import io.opentelemetry.semconv.OtelAttributes;
import io.opentelemetry.semconv.OtelAttributes.OtelStatusCodeValues;
import io.opentelemetry.semconv.SchemaUrls;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.TelemetryAttributes;
import io.opentelemetry.semconv.TelemetryAttributes.TelemetrySdkLanguageValues;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class Opentelemetry_semconvTest {
    @Test
    void schemaUrlsExposeSupportedSemanticConventionVersions() {
        assertThat(SchemaUrls.V1_25_0).isEqualTo("https://opentelemetry.io/schemas/1.25.0");
        assertThat(SchemaUrls.V1_24_0).isEqualTo("https://opentelemetry.io/schemas/1.24.0");
        assertThat(SchemaUrls.V1_23_1).isEqualTo("https://opentelemetry.io/schemas/1.23.1");
        assertThat(SchemaUrls.V1_22_0).isEqualTo("https://opentelemetry.io/schemas/1.22.0");
    }

    @Test
    void attributeKeyTemplatesCreateTypedAndCachedKeys() {
        AttributeKeyTemplate<String> stringTemplate = AttributeKeyTemplate.stringKeyTemplate("test.string");
        AttributeKeyTemplate<List<String>> stringArrayTemplate =
                AttributeKeyTemplate.stringArrayKeyTemplate("test.string_array");
        AttributeKeyTemplate<Boolean> booleanTemplate = AttributeKeyTemplate.booleanKeyTemplate("test.boolean");
        AttributeKeyTemplate<List<Boolean>> booleanArrayTemplate =
                AttributeKeyTemplate.booleanArrayKeyTemplate("test.boolean_array");
        AttributeKeyTemplate<Long> longTemplate = AttributeKeyTemplate.longKeyTemplate("test.long");
        AttributeKeyTemplate<List<Long>> longArrayTemplate =
                AttributeKeyTemplate.longArrayKeyTemplate("test.long_array");
        AttributeKeyTemplate<Double> doubleTemplate = AttributeKeyTemplate.doubleKeyTemplate("test.double");
        AttributeKeyTemplate<List<Double>> doubleArrayTemplate =
                AttributeKeyTemplate.doubleArrayKeyTemplate("test.double_array");

        assertThat(stringTemplate.getAttributeKey("name")).isEqualTo(AttributeKey.stringKey("test.string.name"));
        assertThat(stringArrayTemplate.getAttributeKey("names"))
                .isEqualTo(AttributeKey.stringArrayKey("test.string_array.names"));
        assertThat(booleanTemplate.getAttributeKey("enabled"))
                .isEqualTo(AttributeKey.booleanKey("test.boolean.enabled"));
        assertThat(booleanArrayTemplate.getAttributeKey("flags"))
                .isEqualTo(AttributeKey.booleanArrayKey("test.boolean_array.flags"));
        assertThat(longTemplate.getAttributeKey("count")).isEqualTo(AttributeKey.longKey("test.long.count"));
        assertThat(longArrayTemplate.getAttributeKey("counts"))
                .isEqualTo(AttributeKey.longArrayKey("test.long_array.counts"));
        assertThat(doubleTemplate.getAttributeKey("ratio")).isEqualTo(AttributeKey.doubleKey("test.double.ratio"));
        assertThat(doubleArrayTemplate.getAttributeKey("ratios"))
                .isEqualTo(AttributeKey.doubleArrayKey("test.double_array.ratios"));
        assertThat(stringTemplate.getAttributeKey("name")).isSameAs(stringTemplate.getAttributeKey("name"));
        assertThat(stringTemplate.getAttributeKey("other")).isNotSameAs(stringTemplate.getAttributeKey("name"));
    }

    @Test
    void attributeKeyTemplatesReturnCachedKeysAcrossConcurrentAccess() throws Exception {
        AttributeKeyTemplate<String> template = AttributeKeyTemplate.stringKeyTemplate("test.concurrent");
        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            List<Callable<AttributeKey<String>>> tasks = IntStream.range(0, 16)
                    .mapToObj(index -> (Callable<AttributeKey<String>>) () -> template.getAttributeKey("tenant"))
                    .toList();
            List<Future<AttributeKey<String>>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            AttributeKey<String> expectedKey = template.getAttributeKey("tenant");

            for (Future<AttributeKey<String>> future : futures) {
                assertThat(future.isDone()).isTrue();
                assertThat(future.isCancelled()).isFalse();
                assertThat(future.get(1, TimeUnit.SECONDS)).isSameAs(expectedKey);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void httpAttributeConstantsAndHeaderTemplatesWorkWithAttributes() {
        AttributeKey<List<String>> requestHeader = HttpAttributes.HTTP_REQUEST_HEADER.getAttributeKey("content-type");
        AttributeKey<List<String>> responseHeader = HttpAttributes.HTTP_RESPONSE_HEADER.getAttributeKey("set-cookie");
        Attributes attributes = Attributes.builder()
                .put(HttpAttributes.HTTP_REQUEST_METHOD, HttpRequestMethodValues.POST)
                .put(HttpAttributes.HTTP_REQUEST_METHOD_ORIGINAL, "post")
                .put(HttpAttributes.HTTP_REQUEST_RESEND_COUNT, 2L)
                .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 201L)
                .put(HttpAttributes.HTTP_ROUTE, "/orders/{orderId}")
                .put(requestHeader, List.of("application/json"))
                .put(responseHeader, List.of("session=abc", "theme=dark"))
                .build();

        assertThat(HttpAttributes.HTTP_REQUEST_METHOD).isEqualTo(AttributeKey.stringKey("http.request.method"));
        assertThat(HttpAttributes.HTTP_RESPONSE_STATUS_CODE)
                .isEqualTo(AttributeKey.longKey("http.response.status_code"));
        assertThat(requestHeader).isEqualTo(AttributeKey.stringArrayKey("http.request.header.content-type"));
        assertThat(responseHeader).isEqualTo(AttributeKey.stringArrayKey("http.response.header.set-cookie"));
        assertThat(HttpAttributes.HTTP_REQUEST_HEADER.getAttributeKey("content-type")).isSameAs(requestHeader);
        assertThat(attributes.get(HttpAttributes.HTTP_REQUEST_METHOD)).isEqualTo("POST");
        assertThat(attributes.get(HttpAttributes.HTTP_REQUEST_METHOD_ORIGINAL)).isEqualTo("post");
        assertThat(attributes.get(HttpAttributes.HTTP_REQUEST_RESEND_COUNT)).isEqualTo(2L);
        assertThat(attributes.get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE)).isEqualTo(201L);
        assertThat(attributes.get(HttpAttributes.HTTP_ROUTE)).isEqualTo("/orders/{orderId}");
        assertThat(attributes.get(requestHeader)).containsExactly("application/json");
        assertThat(attributes.get(responseHeader)).containsExactly("session=abc", "theme=dark");
        assertThat(HttpRequestMethodValues.GET).isEqualTo("GET");
        assertThat(HttpRequestMethodValues.OTHER).isEqualTo("_OTHER");
    }

    @Test
    void standardHttpRequestMethodValueConstantsCanBeStoredAsAttributes() {
        List<String> standardMethods = List.of(
                HttpRequestMethodValues.CONNECT,
                HttpRequestMethodValues.DELETE,
                HttpRequestMethodValues.HEAD,
                HttpRequestMethodValues.OPTIONS,
                HttpRequestMethodValues.PATCH,
                HttpRequestMethodValues.PUT,
                HttpRequestMethodValues.TRACE);

        assertThat(standardMethods).containsExactly("CONNECT", "DELETE", "HEAD", "OPTIONS", "PATCH", "PUT", "TRACE");
        for (String method : standardMethods) {
            Attributes attributes = Attributes.of(HttpAttributes.HTTP_REQUEST_METHOD, method);

            assertThat(attributes.get(HttpAttributes.HTTP_REQUEST_METHOD)).isEqualTo(method);
        }
    }

    @Test
    void networkClientServerUrlAndUserAgentAttributesWorkTogether() {
        Attributes attributes = Attributes.builder()
                .put(ClientAttributes.CLIENT_ADDRESS, "198.51.100.7")
                .put(ClientAttributes.CLIENT_PORT, 54433L)
                .put(ServerAttributes.SERVER_ADDRESS, "api.example.test")
                .put(ServerAttributes.SERVER_PORT, 443L)
                .put(NetworkAttributes.NETWORK_LOCAL_ADDRESS, "10.0.0.10")
                .put(NetworkAttributes.NETWORK_LOCAL_PORT, 8443L)
                .put(NetworkAttributes.NETWORK_PEER_ADDRESS, "198.51.100.7")
                .put(NetworkAttributes.NETWORK_PEER_PORT, 54433L)
                .put(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http")
                .put(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2")
                .put(NetworkAttributes.NETWORK_TRANSPORT, NetworkTransportValues.TCP)
                .put(NetworkAttributes.NETWORK_TYPE, NetworkTypeValues.IPV4)
                .put(UrlAttributes.URL_SCHEME, "https")
                .put(UrlAttributes.URL_FULL, "https://api.example.test/orders/42?include=items#summary")
                .put(UrlAttributes.URL_PATH, "/orders/42")
                .put(UrlAttributes.URL_QUERY, "include=items")
                .put(UrlAttributes.URL_FRAGMENT, "summary")
                .put(UserAgentAttributes.USER_AGENT_ORIGINAL, "semconv-test/1.0")
                .build();

        assertThat(ClientAttributes.CLIENT_ADDRESS).isEqualTo(AttributeKey.stringKey("client.address"));
        assertThat(ClientAttributes.CLIENT_PORT).isEqualTo(AttributeKey.longKey("client.port"));
        assertThat(ServerAttributes.SERVER_ADDRESS).isEqualTo(AttributeKey.stringKey("server.address"));
        assertThat(ServerAttributes.SERVER_PORT).isEqualTo(AttributeKey.longKey("server.port"));
        assertThat(NetworkAttributes.NETWORK_TRANSPORT).isEqualTo(AttributeKey.stringKey("network.transport"));
        assertThat(NetworkAttributes.NETWORK_TYPE).isEqualTo(AttributeKey.stringKey("network.type"));
        assertThat(UrlAttributes.URL_FULL).isEqualTo(AttributeKey.stringKey("url.full"));
        assertThat(UserAgentAttributes.USER_AGENT_ORIGINAL).isEqualTo(AttributeKey.stringKey("user_agent.original"));
        assertThat(attributes.get(ClientAttributes.CLIENT_ADDRESS)).isEqualTo("198.51.100.7");
        assertThat(attributes.get(ClientAttributes.CLIENT_PORT)).isEqualTo(54433L);
        assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS)).isEqualTo("api.example.test");
        assertThat(attributes.get(ServerAttributes.SERVER_PORT)).isEqualTo(443L);
        assertThat(attributes.get(NetworkAttributes.NETWORK_LOCAL_ADDRESS)).isEqualTo("10.0.0.10");
        assertThat(attributes.get(NetworkAttributes.NETWORK_LOCAL_PORT)).isEqualTo(8443L);
        assertThat(attributes.get(NetworkAttributes.NETWORK_PEER_ADDRESS)).isEqualTo("198.51.100.7");
        assertThat(attributes.get(NetworkAttributes.NETWORK_PEER_PORT)).isEqualTo(54433L);
        assertThat(attributes.get(NetworkAttributes.NETWORK_PROTOCOL_NAME)).isEqualTo("http");
        assertThat(attributes.get(NetworkAttributes.NETWORK_PROTOCOL_VERSION)).isEqualTo("2");
        assertThat(attributes.get(NetworkAttributes.NETWORK_TRANSPORT)).isEqualTo("tcp");
        assertThat(attributes.get(NetworkAttributes.NETWORK_TYPE)).isEqualTo("ipv4");
        assertThat(attributes.get(UrlAttributes.URL_SCHEME)).isEqualTo("https");
        assertThat(attributes.get(UrlAttributes.URL_PATH)).isEqualTo("/orders/42");
        assertThat(attributes.get(UrlAttributes.URL_QUERY)).isEqualTo("include=items");
        assertThat(attributes.get(UrlAttributes.URL_FRAGMENT)).isEqualTo("summary");
        assertThat(attributes.get(UserAgentAttributes.USER_AGENT_ORIGINAL)).isEqualTo("semconv-test/1.0");
        assertThat(NetworkTransportValues.UNIX).isEqualTo("unix");
        assertThat(NetworkTypeValues.IPV6).isEqualTo("ipv6");
    }

    @Test
    void exceptionErrorOtelJvmServiceAndTelemetryAttributesWorkTogether() {
        Attributes attributes = Attributes.builder()
                .put(ErrorAttributes.ERROR_TYPE, ErrorTypeValues.OTHER)
                .put(ExceptionAttributes.EXCEPTION_TYPE, "java.lang.IllegalArgumentException")
                .put(ExceptionAttributes.EXCEPTION_MESSAGE, "bad input")
                .put(ExceptionAttributes.EXCEPTION_STACKTRACE, "stack trace omitted")
                .put(ExceptionAttributes.EXCEPTION_ESCAPED, true)
                .put(OtelAttributes.OTEL_SCOPE_NAME, "io.opentelemetry.semconv.test")
                .put(OtelAttributes.OTEL_SCOPE_VERSION, "1.0.0")
                .put(OtelAttributes.OTEL_STATUS_CODE, OtelStatusCodeValues.ERROR)
                .put(OtelAttributes.OTEL_STATUS_DESCRIPTION, "request failed")
                .put(JvmAttributes.JVM_GC_ACTION, "end of minor GC")
                .put(JvmAttributes.JVM_GC_NAME, "G1 Young Generation")
                .put(JvmAttributes.JVM_MEMORY_POOL_NAME, "G1 Eden Space")
                .put(JvmAttributes.JVM_MEMORY_TYPE, JvmMemoryTypeValues.HEAP)
                .put(JvmAttributes.JVM_THREAD_DAEMON, false)
                .put(JvmAttributes.JVM_THREAD_STATE, JvmThreadStateValues.RUNNABLE)
                .put(ServiceAttributes.SERVICE_NAME, "checkout")
                .put(ServiceAttributes.SERVICE_VERSION, "2024.04")
                .put(TelemetryAttributes.TELEMETRY_SDK_LANGUAGE, TelemetrySdkLanguageValues.JAVA)
                .put(TelemetryAttributes.TELEMETRY_SDK_NAME, "opentelemetry")
                .put(TelemetryAttributes.TELEMETRY_SDK_VERSION, "test-version")
                .build();

        assertThat(ErrorAttributes.ERROR_TYPE).isEqualTo(AttributeKey.stringKey("error.type"));
        assertThat(ExceptionAttributes.EXCEPTION_ESCAPED).isEqualTo(AttributeKey.booleanKey("exception.escaped"));
        assertThat(OtelAttributes.OTEL_STATUS_CODE).isEqualTo(AttributeKey.stringKey("otel.status_code"));
        assertThat(JvmAttributes.JVM_THREAD_DAEMON).isEqualTo(AttributeKey.booleanKey("jvm.thread.daemon"));
        assertThat(ServiceAttributes.SERVICE_NAME).isEqualTo(AttributeKey.stringKey("service.name"));
        assertThat(TelemetryAttributes.TELEMETRY_SDK_LANGUAGE)
                .isEqualTo(AttributeKey.stringKey("telemetry.sdk.language"));
        assertThat(attributes.get(ErrorAttributes.ERROR_TYPE)).isEqualTo("_OTHER");
        assertThat(attributes.get(ExceptionAttributes.EXCEPTION_TYPE))
                .isEqualTo("java.lang.IllegalArgumentException");
        assertThat(attributes.get(ExceptionAttributes.EXCEPTION_MESSAGE)).isEqualTo("bad input");
        assertThat(attributes.get(ExceptionAttributes.EXCEPTION_STACKTRACE)).isEqualTo("stack trace omitted");
        assertThat(attributes.get(ExceptionAttributes.EXCEPTION_ESCAPED)).isTrue();
        assertThat(attributes.get(OtelAttributes.OTEL_SCOPE_NAME)).isEqualTo("io.opentelemetry.semconv.test");
        assertThat(attributes.get(OtelAttributes.OTEL_SCOPE_VERSION)).isEqualTo("1.0.0");
        assertThat(attributes.get(OtelAttributes.OTEL_STATUS_CODE)).isEqualTo("ERROR");
        assertThat(attributes.get(OtelAttributes.OTEL_STATUS_DESCRIPTION)).isEqualTo("request failed");
        assertThat(attributes.get(JvmAttributes.JVM_GC_ACTION)).isEqualTo("end of minor GC");
        assertThat(attributes.get(JvmAttributes.JVM_GC_NAME)).isEqualTo("G1 Young Generation");
        assertThat(attributes.get(JvmAttributes.JVM_MEMORY_POOL_NAME)).isEqualTo("G1 Eden Space");
        assertThat(attributes.get(JvmAttributes.JVM_MEMORY_TYPE)).isEqualTo("heap");
        assertThat(attributes.get(JvmAttributes.JVM_THREAD_DAEMON)).isFalse();
        assertThat(attributes.get(JvmAttributes.JVM_THREAD_STATE)).isEqualTo("runnable");
        assertThat(attributes.get(ServiceAttributes.SERVICE_NAME)).isEqualTo("checkout");
        assertThat(attributes.get(ServiceAttributes.SERVICE_VERSION)).isEqualTo("2024.04");
        assertThat(attributes.get(TelemetryAttributes.TELEMETRY_SDK_LANGUAGE)).isEqualTo("java");
        assertThat(attributes.get(TelemetryAttributes.TELEMETRY_SDK_NAME)).isEqualTo("opentelemetry");
        assertThat(attributes.get(TelemetryAttributes.TELEMETRY_SDK_VERSION)).isEqualTo("test-version");
        assertThat(OtelStatusCodeValues.OK).isEqualTo("OK");
        assertThat(JvmMemoryTypeValues.NON_HEAP).isEqualTo("non_heap");
        assertThat(JvmThreadStateValues.TIMED_WAITING).isEqualTo("timed_waiting");
        assertThat(TelemetrySdkLanguageValues.PYTHON).isEqualTo("python");
    }
}
