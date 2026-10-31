/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_http_codec;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.boot.http.codec.autoconfigure.CodecsAutoConfiguration;
import org.springframework.boot.http.codec.autoconfigure.HttpCodecsProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.FormHttpMessageReader;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class CodecsAutoConfigurationTest {
    @Test
    void defaultCustomizerPreservesCodecLimitsAndDisablesSensitiveRequestLogging() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CodecsAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(CodecCustomizer.class)).hasSize(1);
            HttpCodecsProperties properties = context.getBean(HttpCodecsProperties.class);
            assertThat(properties.getMaxInMemorySize()).isNull();
            assertThat(properties.isLogRequestDetails()).isFalse();

            ClientCodecConfigurer configurer = ClientCodecConfigurer.create();
            FormHttpMessageReader formReader = formReader(configurer);
            int originalLimit = formReader.getMaxInMemorySize();
            configurer.defaultCodecs().enableLoggingRequestDetails(true);
            applyCustomizers(context, configurer);

            assertThat(formReader(configurer).isEnableLoggingRequestDetails()).isFalse();
            assertThat(formReader(configurer).getMaxInMemorySize()).isEqualTo(originalLimit);
        }
    }

    @Test
    void boundPropertiesCustomizeBothClientAndServerCodecs() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("codec-settings", Map.of(
                    "spring.http.codecs.log-request-details", "true",
                    "spring.http.codecs.max-in-memory-size", "32KB")));
            context.register(CodecsAutoConfiguration.class);
            context.refresh();

            HttpCodecsProperties properties = context.getBean(HttpCodecsProperties.class);
            assertThat(properties.isLogRequestDetails()).isTrue();
            assertThat(properties.getMaxInMemorySize()).isEqualTo(DataSize.ofKilobytes(32));

            for (CodecConfigurer configurer : List.of(ClientCodecConfigurer.create(), ServerCodecConfigurer.create())) {
                applyCustomizers(context, configurer);

                assertThat(formReader(configurer).isEnableLoggingRequestDetails()).isTrue();
                assertThat(formReader(configurer).getMaxInMemorySize()).isEqualTo(32768);
                List<StringDecoder> stringDecoders = configurer.getReaders().stream()
                        .filter(DecoderHttpMessageReader.class::isInstance)
                        .map(reader -> ((DecoderHttpMessageReader<?>) reader).getDecoder())
                        .filter(StringDecoder.class::isInstance).map(StringDecoder.class::cast).toList();
                assertThat(stringDecoders).isNotEmpty();
                assertThat(stringDecoders).allSatisfy(decoder ->
                        assertThat(decoder.getMaxInMemorySize()).isEqualTo(32768));
            }
        }
    }

    @Test
    void jacksonCustomizerUsesApplicationMapperForJsonRoundTrip() {
        JsonMapper mapper = JsonMapper.builder().build();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JsonMapper.class, () -> mapper);
            context.register(CodecsAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(CodecCustomizer.class)).hasSize(2);
            ClientCodecConfigurer configurer = ClientCodecConfigurer.create();
            applyCustomizers(context, configurer);
            JacksonJsonDecoder decoder = configurer.getReaders().stream()
                    .filter(DecoderHttpMessageReader.class::isInstance)
                    .map(reader -> ((DecoderHttpMessageReader<?>) reader).getDecoder())
                    .filter(JacksonJsonDecoder.class::isInstance).map(JacksonJsonDecoder.class::cast)
                    .findFirst().orElseThrow();
            JacksonJsonEncoder encoder = configurer.getWriters().stream()
                    .filter(EncoderHttpMessageWriter.class::isInstance)
                    .map(writer -> ((EncoderHttpMessageWriter<?>) writer).getEncoder())
                    .filter(JacksonJsonEncoder.class::isInstance).map(JacksonJsonEncoder.class::cast)
                    .findFirst().orElseThrow();

            assertThat(decoder.getMapper()).isSameAs(mapper);
            assertThat(encoder.getMapper()).isSameAs(mapper);

            Map<String, Object> message = Map.of("message", "hello codecs", "count", 3);
            ResolvableType type = ResolvableType.forClass(Map.class);
            DataBuffer encoded = encoder.encodeValue(message, DefaultDataBufferFactory.sharedInstance,
                    type, MediaType.APPLICATION_JSON, Map.of());
            String json;
            try {
                json = encoded.toString(StandardCharsets.UTF_8);
                assertThat(mapper.readTree(json).get("message").asString()).isEqualTo("hello codecs");
            } finally {
                DataBufferUtils.release(encoded);
            }
            DataBuffer input = DefaultDataBufferFactory.sharedInstance.wrap(json.getBytes(StandardCharsets.UTF_8));
            Object decoded = decoder.decode(input, type, MediaType.APPLICATION_JSON, Map.of());

            assertThat(decoded).isEqualTo(message);
        }
    }

    private static void applyCustomizers(AnnotationConfigApplicationContext context, CodecConfigurer configurer) {
        context.getBeanProvider(CodecCustomizer.class).orderedStream()
                .forEach(customizer -> customizer.customize(configurer));
    }

    private static FormHttpMessageReader formReader(CodecConfigurer configurer) {
        return configurer.getReaders().stream().filter(FormHttpMessageReader.class::isInstance)
                .map(FormHttpMessageReader.class::cast).findFirst().orElseThrow();
    }
}
