/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

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
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.protobuf.ProtobufHttpMessageWriter;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtobufHttpMessageWriterTest {
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void writesMessageHeadersAndBodyFromProtobufBuilderLookup() {
        StringValue value = StringValue.of("spring-web");
        CapturingReactiveHttpOutputMessage outputMessage = new CapturingReactiveHttpOutputMessage();
        ProtobufHttpMessageWriter writer = new ProtobufHttpMessageWriter();

        writer.write(
                Mono.just(value),
                ResolvableType.forClass(StringValue.class),
                null,
                outputMessage,
                Collections.emptyMap())
                .block(WRITE_TIMEOUT);

        assertThat(outputMessage.isCommitted()).isTrue();
        assertThat(outputMessage.getHeaders().getFirst("X-Protobuf-Schema"))
                .isEqualTo("google/protobuf/wrappers.proto");
        assertThat(outputMessage.getHeaders().getFirst("X-Protobuf-Message"))
                .isEqualTo("google.protobuf.StringValue");
        assertThat(outputMessage.getHeaders().getContentLength()).isEqualTo(value.toByteArray().length);
        assertThat(outputMessage.getBody()).isEqualTo(value.toByteArray());
    }

    private static final class CapturingReactiveHttpOutputMessage implements ReactiveHttpOutputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        private final List<Supplier<? extends Mono<Void>>> beforeCommitActions = new ArrayList<>();
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
            this.beforeCommitActions.add(action);
        }

        @Override
        public boolean isCommitted() {
            return this.committed;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> bodyPublisher) {
            return commit()
                    .thenMany(Flux.from(bodyPublisher).doOnNext(this::copyBuffer))
                    .then();
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> bodyPublisher) {
            return writeWith(Flux.from(bodyPublisher).flatMap(Flux::from));
        }

        @Override
        public Mono<Void> setComplete() {
            return commit();
        }

        byte[] getBody() {
            return this.body.toByteArray();
        }

        private Mono<Void> commit() {
            return Flux.fromIterable(this.beforeCommitActions)
                    .concatMap(action -> action.get())
                    .then(Mono.fromRunnable(() -> this.committed = true));
        }

        private void copyBuffer(DataBuffer buffer) {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            this.body.write(bytes, 0, bytes.length);
            DataBufferUtils.release(buffer);
        }
    }
}
