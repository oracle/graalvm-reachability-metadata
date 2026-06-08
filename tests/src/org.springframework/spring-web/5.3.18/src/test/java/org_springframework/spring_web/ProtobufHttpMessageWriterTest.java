/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.google.protobuf.StringValue;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.protobuf.ProtobufHttpMessageWriter;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtobufHttpMessageWriterTest {

    @Test
    void writesFluxWithProtobufHeadersAndDelimitedContentType() throws Exception {
        ProtobufHttpMessageWriter writer = new ProtobufHttpMessageWriter();
        StringValue value = StringValue.of("spring-web");
        CapturingOutputMessage outputMessage = new CapturingOutputMessage();
        MediaType protobufMediaType = new MediaType("application", "x-protobuf");

        writer.write(Flux.just(value), ResolvableType.forClass(StringValue.class), protobufMediaType,
                outputMessage, Collections.emptyMap()).block(Duration.ofSeconds(10));

        assertThat(outputMessage.getHeaders().getFirst("X-Protobuf-Schema"))
                .isEqualTo("google/protobuf/wrappers.proto");
        assertThat(outputMessage.getHeaders().getFirst("X-Protobuf-Message"))
                .isEqualTo("google.protobuf.StringValue");
        assertThat(outputMessage.getHeaders().getContentType())
                .isEqualTo(new MediaType("application", "x-protobuf",
                        Collections.singletonMap("delimited", "true")));

        StringValue parsed = StringValue.parseDelimitedFrom(
                new ByteArrayInputStream(outputMessage.bodyBytes()));
        assertThat(parsed).isEqualTo(value);
    }

    private static final class CapturingOutputMessage implements ReactiveHttpOutputMessage {

        private final HttpHeaders headers = new HttpHeaders();

        private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

        private final List<Supplier<? extends Mono<Void>>> commitActions = new ArrayList<>();

        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        private boolean committed;

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return this.bufferFactory;
        }

        @Override
        public void beforeCommit(Supplier<? extends Mono<Void>> action) {
            this.commitActions.add(action);
        }

        @Override
        public boolean isCommitted() {
            return this.committed;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return doCommit().thenMany(Flux.from(body).doOnNext(this::captureAndRelease)).then();
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return doCommit().thenMany(Flux.from(body)
                    .concatMap(buffers -> Flux.from(buffers).doOnNext(this::captureAndRelease))).then();
        }

        @Override
        public Mono<Void> setComplete() {
            return doCommit();
        }

        byte[] bodyBytes() {
            return this.body.toByteArray();
        }

        private Mono<Void> doCommit() {
            return Mono.defer(() -> {
                if (this.committed) {
                    return Mono.empty();
                }
                this.committed = true;
                return Flux.fromIterable(this.commitActions)
                        .concatMap(Supplier::get)
                        .then();
            });
        }

        private void captureAndRelease(DataBuffer dataBuffer) {
            try {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                this.body.write(bytes, 0, bytes.length);
            }
            finally {
                DataBufferUtils.release(dataBuffer);
            }
        }
    }
}
