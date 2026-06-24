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
import org.springframework.http.MediaType;
import org.springframework.http.codec.protobuf.ProtobufDecoder;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtobufDecoderTest {

    @Test
    void decodesSingleProtobufMessageWithPublicDecoderApi() {
        ProtobufDecoder decoder = new ProtobufDecoder();
        StringValue value = StringValue.of("spring-web decoder");
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(value.toByteArray());

        Message decoded = decoder.decodeToMono(
                Mono.just(dataBuffer), ResolvableType.forClass(StringValue.class),
                MediaType.APPLICATION_OCTET_STREAM, Collections.emptyMap())
                .block(Duration.ofSeconds(10));

        assertThat(decoded).isEqualTo(value);
    }
}
