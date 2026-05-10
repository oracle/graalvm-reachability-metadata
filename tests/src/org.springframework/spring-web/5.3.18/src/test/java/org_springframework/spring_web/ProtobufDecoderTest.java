/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.time.Duration;
import java.util.Collections;

import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.protobuf.ProtobufDecoder;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtobufDecoderTest {
    private static final Duration DECODE_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void decodesSingleMessageUsingProtobufBuilderLookup() {
        StringValue expected = StringValue.of("spring-web decoder");
        DataBuffer input = DefaultDataBufferFactory.sharedInstance.wrap(expected.toByteArray());
        ProtobufDecoder decoder = new ProtobufDecoder();

        Message decoded = decoder.decodeToMono(
                Mono.just(input),
                ResolvableType.forClass(StringValue.class),
                null,
                Collections.emptyMap())
                .block(DECODE_TIMEOUT);

        assertThat(decoded).isEqualTo(expected);
    }
}
