/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;

import com.google.protobuf.Int32Value;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.protobuf.ProtobufJsonDecoder;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtobufJsonDecoderTest {

    @Test
    void decodesJsonStringToProtobufMessage() {
        ProtobufJsonDecoder decoder = new ProtobufJsonDecoder();
        ResolvableType messageType = ResolvableType.forClass(StringValue.class);
        DataBuffer json = DefaultDataBufferFactory.sharedInstance.wrap(
                "\"spring JSON\"".getBytes(StandardCharsets.UTF_8));

        assertThat(decoder.canDecode(messageType, MediaType.APPLICATION_JSON)).isTrue();
        Message decoded = decoder.decode(json, messageType,
                MediaType.APPLICATION_JSON, Collections.emptyMap());

        assertThat(decoded).isInstanceOf(StringValue.class);
        assertThat(((StringValue) decoded).getValue()).isEqualTo("spring JSON");
    }

    @Test
    void aggregatesChunkedJsonBeforeDecodingProtobufMessage() {
        ProtobufJsonDecoder decoder = new ProtobufJsonDecoder();
        DataBuffer firstChunk = DefaultDataBufferFactory.sharedInstance.wrap(
                "12".getBytes(StandardCharsets.UTF_8));
        DataBuffer secondChunk = DefaultDataBufferFactory.sharedInstance.wrap(
                "34".getBytes(StandardCharsets.UTF_8));

        Message decoded = decoder.decodeToMono(Flux.just(firstChunk, secondChunk),
                ResolvableType.forClass(Int32Value.class),
                MediaType.APPLICATION_JSON, Collections.emptyMap())
                .block(Duration.ofSeconds(10));

        assertThat(decoded).isInstanceOf(Int32Value.class);
        assertThat(((Int32Value) decoded).getValue()).isEqualTo(1234);
    }
}
