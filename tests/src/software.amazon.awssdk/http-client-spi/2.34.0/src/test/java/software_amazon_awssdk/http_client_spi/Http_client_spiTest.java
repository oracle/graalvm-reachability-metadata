/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.http_client_spi;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.http.Abortable;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.Header;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.http.HttpStatusFamily;
import software.amazon.awssdk.http.Protocol;
import software.amazon.awssdk.http.ProtocolNegotiation;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.SdkHttpExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.SdkRequestContext;
import software.amazon.awssdk.http.TlsKeyManagersProvider;
import software.amazon.awssdk.http.async.AbortableInputStreamSubscriber;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;
import software.amazon.awssdk.http.async.SdkHttpContentPublisher;
import software.amazon.awssdk.http.async.SimpleSubscriber;
import software.amazon.awssdk.utils.AttributeMap;

public class Http_client_spiTest {
    private static final TextExecutionAttribute TEXT_ATTRIBUTE = new TextExecutionAttribute();

    @Test
    void fullRequestBuilderPreservesUriHeadersQueryParametersAndContent() throws Exception {
        ContentStreamProvider content = ContentStreamProvider.fromUtf8String("request payload");

        SdkHttpFullRequest request = SdkHttpFullRequest.builder()
            .uri(URI.create("https://example.com:8443/service/path?fromUri=1"))
            .method(SdkHttpMethod.POST)
            .appendRawQueryParameter("q", "one")
            .appendRawQueryParameter("q", "two words")
            .putHeader(Header.CONTENT_TYPE, "text/plain")
            .appendHeader("X-Test", "alpha")
            .appendHeader("X-Test", "beta")
            .contentStreamProvider(content)
            .build();

        assertThat(request.protocol()).isEqualTo("https");
        assertThat(request.host()).isEqualTo("example.com");
        assertThat(request.port()).isEqualTo(8443);
        assertThat(request.encodedPath()).isEqualTo("/service/path");
        assertThat(request.method()).isEqualTo(SdkHttpMethod.POST);
        assertThat(request.numRawQueryParameters()).isEqualTo(2);
        assertThat(request.firstMatchingRawQueryParameter("fromUri")).contains("1");
        assertThat(request.firstMatchingRawQueryParameter(List.of("missing", "q"))).contains("one");
        assertThat(request.firstMatchingRawQueryParameters("q")).containsExactly("one", "two words");
        assertThat(request.encodedQueryParameters()).hasValueSatisfying(query -> {
            assertThat(query).contains("fromUri=1", "q=one");
            assertThat(query).containsAnyOf("q=two%20words", "q=two+words");
        });
        assertThat(request.getUri().toString()).startsWith("https://example.com:8443/service/path?");
        assertThat(request.firstMatchingHeader("content-type")).contains("text/plain");
        assertThat(request.matchingHeaders("x-test")).containsExactly("alpha", "beta");
        assertThat(request.anyMatchingHeader("X-Test"::equals)).isTrue();
        assertThat(request.contentStreamProvider()).containsSame(content);
        assertThat(readUtf8(request.contentStreamProvider().orElseThrow())).isEqualTo("request payload");

        SdkHttpFullRequest mutated = request.toBuilder()
            .removeHeader(Header.CONTENT_TYPE)
            .putHeader(Header.ACCEPT, "application/json")
            .removeQueryParameter("fromUri")
            .method(SdkHttpMethod.PUT)
            .build();

        assertThat(request.firstMatchingHeader(Header.CONTENT_TYPE)).contains("text/plain");
        assertThat(request.firstMatchingRawQueryParameter("fromUri")).contains("1");
        assertThat(mutated.method()).isEqualTo(SdkHttpMethod.PUT);
        assertThat(mutated.firstMatchingHeader(Header.CONTENT_TYPE)).isEmpty();
        assertThat(mutated.firstMatchingHeader(Header.ACCEPT)).contains("application/json");
        assertThat(mutated.firstMatchingRawQueryParameter("fromUri")).isEmpty();
    }

    @Test
    void queryParametersCanBeEncodedAsFormData() {
        SdkHttpRequest request = SdkHttpRequest.builder()
            .protocol("https")
            .host("example.com")
            .encodedPath("/submit")
            .method(SdkHttpMethod.POST)
            .putRawQueryParameter("multi", List.of("one", "two"))
            .putRawQueryParameter("space", "two words")
            .putRawQueryParameter("symbols", "a+b&c=d")
            .build();

        assertThat(request.encodedQueryParametersAsFormData()).hasValueSatisfying(formData ->
            assertThat(formData.split("&")).containsExactlyInAnyOrder(
                "multi=one",
                "multi=two",
                "space=two+words",
                "symbols=a%2Bb%26c%3Dd"));
    }

    @Test
    void contentStreamProvidersCreateExpectedStreams() throws Exception {
        byte[] safeBytes = "safe".getBytes(UTF_8);
        ContentStreamProvider safeProvider = ContentStreamProvider.fromByteArray(safeBytes);
        safeBytes[0] = 'S';

        assertThat(readUtf8(safeProvider)).isEqualTo("safe");
        assertThat(readUtf8(safeProvider)).isEqualTo("safe");
        assertThat(safeProvider.name()).isNotBlank();

        byte[] unsafeBytes = "unsafe".getBytes(UTF_8);
        ContentStreamProvider unsafeProvider = ContentStreamProvider.fromByteArrayUnsafe(unsafeBytes);
        unsafeBytes[0] = 'U';

        assertThat(readUtf8(unsafeProvider)).isEqualTo("Unsafe");

        AtomicInteger streamNumber = new AtomicInteger();
        ContentStreamProvider supplierProvider = ContentStreamProvider.fromInputStreamSupplier(() -> {
            int current = streamNumber.incrementAndGet();
            return new ByteArrayInputStream(("stream-" + current).getBytes(UTF_8));
        });

        assertThat(readUtf8(supplierProvider)).isEqualTo("stream-1");
        assertThat(readUtf8(supplierProvider)).isEqualTo("stream-2");
        assertThat(readUtf8(ContentStreamProvider.fromString("latin", UTF_8))).isEqualTo("latin");
    }

    @Test
    void contentStreamProviderCreatedFromMarkableInputStreamReplaysContent() throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("replayable".getBytes(UTF_8));
        ContentStreamProvider provider = ContentStreamProvider.fromInputStream(inputStream);

        assertThat(readUtf8(provider)).isEqualTo("replayable");
        assertThat(readUtf8(provider)).isEqualTo("replayable");
        assertThat(provider.name()).isEqualTo("Stream");
    }

    @Test
    void abortableInputStreamDelegatesReadsAndAbortCallbacks() throws Exception {
        AtomicBoolean aborted = new AtomicBoolean();
        Abortable abortable = () -> aborted.set(true);
        AbortableInputStream stream = AbortableInputStream.create(
            new ByteArrayInputStream("body".getBytes(UTF_8)), abortable);

        assertThat(stream.delegate()).isInstanceOf(ByteArrayInputStream.class);
        assertThat(stream.read()).isEqualTo((int) 'b');
        assertThat(new String(stream.readAllBytes(), UTF_8)).isEqualTo("ody");

        stream.abort();

        assertThat(aborted).isTrue();
        assertThat(AbortableInputStream.createEmpty().read()).isEqualTo(-1);
    }

    @Test
    void fullResponseAndExecuteResponseExposeHeadersStatusAndContent() throws Exception {
        AbortableInputStream responseContent = AbortableInputStream.create(
            new ByteArrayInputStream("response".getBytes(UTF_8)));
        SdkHttpFullResponse response = SdkHttpFullResponse.builder()
            .statusCode(HttpStatusCode.OK)
            .statusText("OK")
            .putHeader(Header.CONTENT_LENGTH, "8")
            .appendHeader("Set-Cookie", "a=1")
            .appendHeader("Set-Cookie", "b=2")
            .content(responseContent)
            .build();

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.statusText()).contains("OK");
        assertThat(response.firstMatchingHeader(Header.CONTENT_LENGTH)).contains("8");
        assertThat(response.matchingHeaders("set-cookie")).containsExactly("a=1", "b=2");
        assertThat(response.content()).containsSame(responseContent);

        SdkHttpResponse redirected = response.toBuilder()
            .statusCode(HttpStatusCode.MOVED_PERMANENTLY)
            .statusText("Moved Permanently")
            .clearHeaders()
            .putHeader("Location", "https://example.com/new")
            .build();

        assertThat(redirected.isSuccessful()).isFalse();
        assertThat(redirected.statusCode()).isEqualTo(301);
        assertThat(redirected.firstMatchingHeader("location")).contains("https://example.com/new");
        assertThat(response.firstMatchingHeader(Header.CONTENT_LENGTH)).contains("8");

        HttpExecuteResponse executeResponse = HttpExecuteResponse.builder()
            .response(response)
            .responseBody(AbortableInputStream.create(new ByteArrayInputStream("execute".getBytes(UTF_8))))
            .build();

        assertThat(executeResponse.httpResponse()).isSameAs(response);
        assertThat(new String(executeResponse.responseBody().orElseThrow().readAllBytes(), UTF_8))
            .isEqualTo("execute");
    }

    @Test
    void executeRequestAndAsyncExecuteRequestRetainTypedRequestState() throws Exception {
        ContentStreamProvider content = ContentStreamProvider.fromUtf8String("sync-body");
        SdkHttpRequest request = SdkHttpRequest.builder()
            .uri("http://localhost:8080/resource")
            .method(SdkHttpMethod.PATCH)
            .putHeader("X-Mode", "sync")
            .build();

        HttpExecuteRequest executeRequest = HttpExecuteRequest.builder()
            .request(request)
            .contentStreamProvider(content)
            .build();

        assertThat(executeRequest.httpRequest()).isSameAs(request);
        assertThat(executeRequest.contentStreamProvider()).containsSame(content);
        assertThat(executeRequest.metricCollector()).isEmpty();

        SdkHttpExecutionAttributes attributes = SdkHttpExecutionAttributes.builder()
            .put(TEXT_ATTRIBUTE, "attribute-value")
            .build();
        RecordingContentPublisher publisher = new RecordingContentPublisher(
            ByteBuffer.wrap("async".getBytes(UTF_8)));
        RecordingResponseHandler responseHandler = new RecordingResponseHandler();

        AsyncExecuteRequest asyncRequest = AsyncExecuteRequest.builder()
            .request(request)
            .requestContentPublisher(publisher)
            .responseHandler(responseHandler)
            .httpExecutionAttributes(attributes)
            .putHttpExecutionAttribute(TEXT_ATTRIBUTE, "builder-override")
            .fullDuplex(true)
            .build();

        assertThat(asyncRequest.request()).isSameAs(request);
        assertThat(asyncRequest.requestContentPublisher()).isSameAs(publisher);
        assertThat(asyncRequest.requestContentPublisher().contentLength()).contains(5L);
        assertThat(asyncRequest.responseHandler()).isSameAs(responseHandler);
        assertThat(asyncRequest.fullDuplex()).isTrue();
        assertThat(asyncRequest.httpExecutionAttributes().getAttribute(TEXT_ATTRIBUTE))
            .isEqualTo("builder-override");
        assertThat(attributes.getAttribute(TEXT_ATTRIBUTE)).isEqualTo("attribute-value");
        assertThat(asyncRequest.metricCollector()).isEmpty();
        responseHandler.assertUnused();

        SdkRequestContext context = SdkRequestContext.builder().fullDuplex(true).build();

        assertThat(context.fullDuplex()).isTrue();
    }

    @Test
    void executionAttributesCanBeCopiedAndOverridden() {
        SdkHttpExecutionAttributes original = SdkHttpExecutionAttributes.builder()
            .put(TEXT_ATTRIBUTE, "original")
            .build();

        SdkHttpExecutionAttributes copy = original.toBuilder()
            .put(TEXT_ATTRIBUTE, "copy")
            .build();

        assertThat(original.getAttribute(TEXT_ATTRIBUTE)).isEqualTo("original");
        assertThat(copy.getAttribute(TEXT_ATTRIBUTE)).isEqualTo("copy");
        assertThat(copy).isNotEqualTo(original);
    }

    @Test
    void statusFamiliesMethodsAndConfigurationConstantsExposePublicHttpMetadata() {
        assertThat(HttpStatusFamily.of(HttpStatusCode.CONTINUE)).isEqualTo(HttpStatusFamily.INFORMATIONAL);
        assertThat(HttpStatusFamily.of(HttpStatusCode.CREATED)).isEqualTo(HttpStatusFamily.SUCCESSFUL);
        assertThat(HttpStatusFamily.of(HttpStatusCode.TEMPORARY_REDIRECT))
            .isEqualTo(HttpStatusFamily.REDIRECTION);
        assertThat(HttpStatusFamily.of(HttpStatusCode.NOT_FOUND)).isEqualTo(HttpStatusFamily.CLIENT_ERROR);
        assertThat(HttpStatusFamily.of(HttpStatusCode.SERVICE_UNAVAILABLE))
            .isEqualTo(HttpStatusFamily.SERVER_ERROR);
        assertThat(HttpStatusFamily.of(700)).isEqualTo(HttpStatusFamily.OTHER);
        assertThat(HttpStatusFamily.SUCCESSFUL.isOneOf(
            HttpStatusFamily.INFORMATIONAL, HttpStatusFamily.SUCCESSFUL)).isTrue();
        assertThat(HttpStatusFamily.SUCCESSFUL.isOneOf(
            HttpStatusFamily.CLIENT_ERROR, HttpStatusFamily.SERVER_ERROR)).isFalse();

        assertThat(SdkHttpMethod.fromValue("GET")).isEqualTo(SdkHttpMethod.GET);
        assertThat(SdkHttpMethod.fromValue("PATCH")).isEqualTo(SdkHttpMethod.PATCH);
        assertThat(Protocol.valueOf("HTTP1_1")).isEqualTo(Protocol.HTTP1_1);
        assertThat(ProtocolNegotiation.valueOf("ALPN")).isEqualTo(ProtocolNegotiation.ALPN);

        AttributeMap defaults = SdkHttpConfigurationOption.GLOBAL_HTTP_DEFAULTS;
        assertThat(defaults.get(SdkHttpConfigurationOption.READ_TIMEOUT)).isInstanceOf(Duration.class);
        assertThat(defaults.get(SdkHttpConfigurationOption.CONNECTION_TIMEOUT)).isInstanceOf(Duration.class);
        assertThat(defaults.get(SdkHttpConfigurationOption.MAX_CONNECTIONS)).isInstanceOf(Integer.class);
        assertThat(defaults.get(SdkHttpConfigurationOption.PROTOCOL)).isEqualTo(Protocol.HTTP1_1);
        assertThat(defaults.get(SdkHttpConfigurationOption.PROTOCOL_NEGOTIATION))
            .isEqualTo(ProtocolNegotiation.ASSUME_PROTOCOL);
        assertThat(defaults.get(SdkHttpConfigurationOption.TLS_KEY_MANAGERS_PROVIDER))
            .isInstanceOf(TlsKeyManagersProvider.class);
        assertThat(TlsKeyManagersProvider.noneProvider().keyManagers()).isNull();
        assertThat(SdkHttpConfigurationOption.READ_TIMEOUT.name()).isNotBlank();
        assertThat(SdkHttpConfigurationOption.READ_TIMEOUT.toString())
            .contains(SdkHttpConfigurationOption.READ_TIMEOUT.name());
    }

    @Test
    void simpleAndAbortableSubscribersConsumeByteBuffersWithoutBackgroundThreads() throws Exception {
        List<String> chunks = new ArrayList<>();
        SimpleSubscriber simpleSubscriber = new SimpleSubscriber(buffer -> chunks.add(readUtf8(buffer)));
        RecordingSubscription simpleSubscription = new RecordingSubscription();

        simpleSubscriber.onSubscribe(simpleSubscription);
        simpleSubscriber.onNext(ByteBuffer.wrap("one".getBytes(UTF_8)));
        simpleSubscriber.onNext(ByteBuffer.wrap("two".getBytes(UTF_8)));
        simpleSubscriber.onComplete();

        assertThat(simpleSubscription.requested()).isPositive();
        assertThat(chunks).containsExactly("one", "two");

        AtomicBoolean closed = new AtomicBoolean();
        AbortableInputStreamSubscriber abortableSubscriber = AbortableInputStreamSubscriber.builder()
            .doAfterClose(() -> closed.set(true))
            .build();
        RecordingSubscription abortableSubscription = new RecordingSubscription();

        abortableSubscriber.onSubscribe(abortableSubscription);
        abortableSubscriber.onNext(ByteBuffer.wrap("hello ".getBytes(UTF_8)));
        abortableSubscriber.onNext(ByteBuffer.wrap("world".getBytes(UTF_8)));
        abortableSubscriber.onComplete();

        assertThat(abortableSubscription.requested()).isPositive();
        assertThat(new String(abortableSubscriber.readAllBytes(), UTF_8)).isEqualTo("hello world");

        abortableSubscriber.abort();

        assertThat(closed).isTrue();
    }

    @Test
    void customPublisherDeliversContentToSubscriberOnDemand() {
        RecordingContentPublisher publisher = new RecordingContentPublisher(
            ByteBuffer.wrap("publish".getBytes(UTF_8)));
        List<String> chunks = new ArrayList<>();
        SimpleSubscriber subscriber = new SimpleSubscriber(buffer -> chunks.add(readUtf8(buffer)));

        publisher.subscribe(subscriber);

        assertThat(chunks).containsExactly("publish");
        assertThat(publisher.contentLength()).contains(7L);
    }

    private static String readUtf8(ContentStreamProvider provider) throws IOException {
        try (InputStream stream = provider.newStream()) {
            return new String(stream.readAllBytes(), UTF_8);
        }
    }

    private static String readUtf8(ByteBuffer buffer) {
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return new String(bytes, UTF_8);
    }

    private static final class TextExecutionAttribute extends SdkHttpExecutionAttribute<String> {
        private TextExecutionAttribute() {
            super(String.class);
        }
    }

    private static final class RecordingSubscription implements Subscription {
        private final AtomicLong requested = new AtomicLong();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void request(long elements) {
            requested.addAndGet(elements);
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        private long requested() {
            return requested.get();
        }
    }

    private static final class RecordingContentPublisher implements SdkHttpContentPublisher {
        private final ByteBuffer content;

        private RecordingContentPublisher(ByteBuffer content) {
            this.content = content.asReadOnlyBuffer();
        }

        @Override
        public Optional<Long> contentLength() {
            return Optional.of((long) content.remaining());
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                private final AtomicBoolean delivered = new AtomicBoolean();
                private final AtomicBoolean cancelled = new AtomicBoolean();

                @Override
                public void request(long elements) {
                    if (elements <= 0 || cancelled.get() || !delivered.compareAndSet(false, true)) {
                        return;
                    }
                    subscriber.onNext(content.asReadOnlyBuffer());
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        }
    }

    private static final class RecordingResponseHandler implements SdkAsyncHttpResponseHandler {
        private SdkHttpResponse response;
        private Publisher<ByteBuffer> stream;
        private Throwable error;

        @Override
        public void onHeaders(SdkHttpResponse response) {
            this.response = response;
        }

        @Override
        public void onStream(Publisher<ByteBuffer> stream) {
            this.stream = stream;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        private void assertUnused() {
            assertThat(response).isNull();
            assertThat(stream).isNull();
            assertThat(error).isNull();
        }
    }
}
