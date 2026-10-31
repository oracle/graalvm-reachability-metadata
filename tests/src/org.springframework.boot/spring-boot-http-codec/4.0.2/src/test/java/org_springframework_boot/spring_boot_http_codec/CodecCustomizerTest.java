/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_http_codec;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.ServerCodecConfigurer;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

public class CodecCustomizerTest {
    @Test
    void customizerReplacesDefaultReadersForBothClientsAndServers() {
        StringDecoder decoder = StringDecoder.textPlainOnly();
        CodecCustomizer customizer = configurer -> {
            configurer.registerDefaults(false);
            configurer.customCodecs().register(decoder);
        };

        for (CodecConfigurer configurer : List.of(ClientCodecConfigurer.create(), ServerCodecConfigurer.create())) {
            customizer.customize(configurer);

            assertThat(configurer.getReaders()).hasSize(1);
            assertThat(configurer.getReaders().getFirst()).isInstanceOf(DecoderHttpMessageReader.class);
            DecoderHttpMessageReader<?> reader = (DecoderHttpMessageReader<?>) configurer.getReaders().getFirst();
            assertThat(reader.getDecoder()).isSameAs(decoder);
            assertThat(reader.canRead(ResolvableType.forClass(String.class), MediaType.TEXT_PLAIN)).isTrue();
            assertThat(configurer.getWriters()).isEmpty();

            DataBuffer input = DefaultDataBufferFactory.sharedInstance.wrap(
                    "first line\nsecond line\n".getBytes(StandardCharsets.UTF_8));
            List<String> lines = decoder.decode(Flux.just(input), ResolvableType.forClass(String.class),
                    MediaType.TEXT_PLAIN, Map.of()).collectList().block(Duration.ofSeconds(30));

            assertThat(lines).containsExactly("first line", "second line");
        }
    }
}
