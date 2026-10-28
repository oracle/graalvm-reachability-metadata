/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import brave.ScopedSpan;
import brave.Tracer;
import brave.Tracing;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ByteArrayResource;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.ClosedSenderException;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.brave.AsyncZipkinSpanHandler;
import zipkin2.reporter.brave.ZipkinSpanHandler;
import zipkin2.reporter.okhttp3.OkHttpSender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * Spring XML wiring of the reporter through the `zipkin2.reporter.beans` factory beans: senders,
 * `AsyncReporter` and Brave span handlers declared as beans, with their properties converted from
 * XML strings, and closed in dependency order when the application context shuts down.
 *
 * A legacy Spring application keeps its tracing setup in an XML bean file, with the collector URL
 * resolved from an environment property. Each test refreshes such a context, uses the beans the
 * way the application would, and reads back what a JDK `HttpServer` collector received.
 */
public class SpringXmlReporterBeansTest {

    private static final String TRACE_ID = "5af7183fb1d4cf5f4a36c2f1b2d3e4f5";

    private HttpServer collector;
    private final BlockingQueue<Post> posts = new LinkedBlockingQueue<>();
    private String endpoint;

    @BeforeEach
    void startCollector() throws IOException {
        collector = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        collector.createContext("/api/v2/spans", this::receive);
        collector.start();
        endpoint = "http://127.0.0.1:" + collector.getAddress().getPort() + "/api/v2/spans";
    }

    @AfterEach
    void stopCollector() {
        collector.stop(0);
    }

    @Test
    void asyncReporterBeanSendsProto3OverAUrlConnectionSenderBean() throws Exception {
        GenericXmlApplicationContext context = context("""
                <bean id="metrics" class="zipkin2.reporter.InMemoryReporterMetrics"/>

                <bean id="sender" class="zipkin2.reporter.beans.URLConnectionSenderFactoryBean">
                  <property name="endpoint" value="${zipkin.collector.endpoint}"/>
                  <property name="encoding" value="PROTO3"/>
                  <property name="compressionEnabled" value="false"/>
                  <property name="connectTimeout" value="2000"/>
                  <property name="readTimeout" value="5000"/>
                  <property name="messageMaxBytes" value="65536"/>
                </bean>

                <bean id="spanReporter" class="zipkin2.reporter.beans.AsyncReporterFactoryBean">
                  <property name="sender" ref="sender"/>
                  <property name="encoder" value="PROTO3"/>
                  <property name="metrics" ref="metrics"/>
                  <property name="messageTimeout" value="0"/>
                  <property name="closeTimeout" value="1000"/>
                  <property name="queuedMaxSpans" value="100"/>
                </bean>
                """);
        URLConnectionSender sender;
        AsyncReporter<Span> reporter;
        InMemoryReporterMetrics metrics;
        try {
            sender = context.getBean("sender", URLConnectionSender.class);
            reporter = typedReporter(context.getBean("spanReporter", AsyncReporter.class));
            metrics = context.getBean("metrics", InMemoryReporterMetrics.class);
            assertSame(reporter, context.getBean(AsyncReporter.class));
            assertEquals(Encoding.PROTO3, sender.encoding());
            assertEquals(65536, sender.messageMaxBytes());

            List<Span> order = List.of(
                    span("0000000000000a01", null, "post /orders", Span.Kind.SERVER),
                    span("0000000000000a02", "0000000000000a01", "insert order", Span.Kind.CLIENT));
            order.forEach(reporter::report);
            reporter.flush();

            Post post = posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(post);
            assertEquals("application/x-protobuf", post.contentType);
            assertNull(post.contentEncoding);
            assertEquals(order, SpanBytesDecoder.PROTO3.decodeList(post.body));
            assertEquals(1, metrics.messages());
            assertEquals(2, metrics.spans());
        } finally {
            context.close();
        }

        // Closing the context destroys the reporter bean and then the sender bean it references.
        reporter.report(span("0000000000000a03", null, "after shutdown", Span.Kind.SERVER));
        assertEquals(1, metrics.spansDropped());
        assertThrows(ClosedSenderException.class, () -> sender.send(List.of(new byte[] {1})));
        assertTrue(posts.isEmpty());
    }

    @Test
    void asyncZipkinSpanHandlerBeanExportsBraveSpansAsJsonOverOkHttp() throws Exception {
        GenericXmlApplicationContext context = context("""
                <bean id="sender" class="zipkin2.reporter.beans.OkHttpSenderFactoryBean">
                  <property name="endpoint" value="${zipkin.collector.endpoint}"/>
                  <property name="encoding" value="JSON"/>
                  <property name="maxRequests" value="4"/>
                  <property name="writeTimeout" value="5000"/>
                </bean>

                <bean id="zipkinSpanHandler"
                      class="zipkin2.reporter.beans.AsyncZipkinSpanHandlerFactoryBean">
                  <property name="sender" ref="sender"/>
                  <property name="encoder" value="JSON"/>
                  <property name="alwaysReportSpans" value="true"/>
                  <property name="messageTimeout" value="0"/>
                </bean>
                """);
        try {
            OkHttpSender sender = context.getBean("sender", OkHttpSender.class);
            assertEquals(Encoding.JSON, sender.encoding());
            AsyncZipkinSpanHandler handler =
                    context.getBean("zipkinSpanHandler", AsyncZipkinSpanHandler.class);
            assertSame(handler, context.getBean(SpanHandler.class));

            // With alwaysReportSpans, spans recorded locally reach Zipkin though the trace is unsampled.
            Tracing tracing = Tracing.newBuilder()
                    .localServiceName("inventory")
                    .sampler(Sampler.NEVER_SAMPLE)
                    .alwaysSampleLocal()
                    .addSpanHandler(handler)
                    .build();
            try {
                Tracer tracer = tracing.tracer();
                ScopedSpan restock = tracer.startScopedSpan("restock");
                assertTrue(restock.context().sampledLocal());
                restock.tag("sku", "A-1001");
                restock.finish();
            } finally {
                tracing.close();
            }
            handler.flush();

            Post post = posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(post);
            assertEquals("application/json", post.contentType);
            List<Span> spans = SpanBytesDecoder.JSON_V2.decodeList(post.body);
            assertEquals(1, spans.size());
            Span restock = spans.get(0);
            assertEquals("restock", restock.name());
            assertEquals("inventory", restock.localServiceName());
            assertEquals(Map.of("sku", "A-1001"), restock.tags());
        } finally {
            context.close();
        }
    }

    @Test
    void zipkinSpanHandlerBeanDelegatesToAReporterBean() throws Exception {
        GenericXmlApplicationContext context = context("""
                <bean id="sender" class="zipkin2.reporter.beans.URLConnectionSenderFactoryBean">
                  <property name="endpoint" value="${zipkin.collector.endpoint}"/>
                </bean>

                <bean id="spanReporter" class="zipkin2.reporter.beans.AsyncReporterFactoryBean">
                  <property name="sender" ref="sender"/>
                  <property name="messageTimeout" value="0"/>
                </bean>

                <bean id="zipkinSpanHandler"
                      class="zipkin2.reporter.beans.ZipkinSpanHandlerFactoryBean">
                  <property name="spanReporter" ref="spanReporter"/>
                </bean>
                """);
        try {
            AsyncReporter<Span> reporter = typedReporter(context.getBean("spanReporter", AsyncReporter.class));
            SpanHandler handler = context.getBean("zipkinSpanHandler", SpanHandler.class);
            assertInstanceOf(ZipkinSpanHandler.class, handler);

            Tracing tracing = Tracing.newBuilder()
                    .localServiceName("billing")
                    .addSpanHandler(handler)
                    .build();
            try {
                ScopedSpan invoice = tracing.tracer().startScopedSpan("issue invoice");
                invoice.error(new IllegalStateException("card declined"));
                invoice.finish();
            } finally {
                tracing.close();
            }
            reporter.flush();

            Post post = posts.poll(10, TimeUnit.SECONDS);
            assertNotNull(post);
            assertEquals("gzip", post.contentEncoding);
            List<Span> spans = SpanBytesDecoder.JSON_V2.decodeList(post.body);
            assertEquals(1, spans.size());
            assertEquals("issue invoice", spans.get(0).name());
            assertEquals("billing", spans.get(0).localServiceName());
            assertEquals("card declined", spans.get(0).tags().get("error"));
        } finally {
            context.close();
        }
    }

    @Test
    void mismatchedEncoderAndSenderFailContextStartup() {
        GenericXmlApplicationContext context = new GenericXmlApplicationContext();
        context.load(xml("""
                <bean id="sender" class="zipkin2.reporter.beans.URLConnectionSenderFactoryBean">
                  <property name="endpoint" value="http://127.0.0.1:9/api/v2/spans"/>
                  <property name="encoding" value="PROTO3"/>
                </bean>

                <bean id="spanReporter" class="zipkin2.reporter.beans.AsyncReporterFactoryBean">
                  <property name="sender" ref="sender"/>
                  <property name="encoder" value="JSON"/>
                </bean>
                """));
        BeanCreationException failure = assertThrows(BeanCreationException.class, context::refresh);
        assertEquals("spanReporter", failure.getBeanName());
        assertInstanceOf(IllegalArgumentException.class, failure.getMostSpecificCause());
        assertTrue(failure.getMostSpecificCause().getMessage().contains("PROTO3"));
    }

    /// Refreshes a context from the given bean declarations with the collector URL as a property.
    private GenericXmlApplicationContext context(String beans) {
        GenericXmlApplicationContext context = new GenericXmlApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("tracing", Map.of("zipkin.collector.endpoint", endpoint)));
        context.load(xml("""
                <bean class="org.springframework.context.support.PropertySourcesPlaceholderConfigurer"/>
                """ + beans));
        context.refresh();
        return context;
    }

    private static ByteArrayResource xml(String beans) {
        String document = """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans
                         http://www.springframework.org/schema/beans/spring-beans.xsd">
                """ + beans + """
                </beans>
                """;
        return new ByteArrayResource(document.getBytes(StandardCharsets.UTF_8), "tracing.xml");
    }

    @SuppressWarnings("unchecked")
    private static AsyncReporter<Span> typedReporter(AsyncReporter<?> reporter) {
        Reporter<Span> spanReporter = (Reporter<Span>) reporter;
        return (AsyncReporter<Span>) spanReporter;
    }

    private void receive(HttpExchange exchange) throws IOException {
        String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
        byte[] body;
        try (InputStream raw = exchange.getRequestBody();
                InputStream in = "gzip".equals(encoding) ? new GZIPInputStream(raw) : raw) {
            body = in.readAllBytes();
        }
        posts.add(new Post(exchange.getRequestHeaders().getFirst("Content-Type"), encoding, body));
        exchange.sendResponseHeaders(202, -1);
        exchange.close();
    }

    private static Span span(String id, String parentId, String name, Span.Kind kind) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .parentId(parentId)
                .id(id)
                .name(name)
                .kind(kind)
                .localEndpoint(Endpoint.newBuilder().serviceName("orders").ip("10.0.0.9").build())
                .timestamp(1_700_000_000_000_000L)
                .duration(4_200L)
                .build();
    }

    /// One POST as the collector received it, with the body already decompressed.
    private record Post(String contentType, String contentEncoding, byte[] body) {
    }
}
