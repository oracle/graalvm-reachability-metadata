/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_http_codec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.boot.http.codec.autoconfigure.CodecsAutoConfiguration;
import org.springframework.boot.http.codec.autoconfigure.HttpCodecsProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.util.unit.DataSize;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_http_codecTest {

    @Test
    void httpCodecsPropertiesExposeDefaultsAndUpdates() {
        HttpCodecsProperties properties = new HttpCodecsProperties();

        assertThat(properties.isLogRequestDetails()).isFalse();
        assertThat(properties.getMaxInMemorySize()).isNull();

        properties.setLogRequestDetails(true);
        properties.setMaxInMemorySize(DataSize.ofKilobytes(64));

        assertThat(properties.isLogRequestDetails()).isTrue();
        assertThat(properties.getMaxInMemorySize()).isEqualTo(DataSize.ofKilobytes(64));
    }

    @Test
    void autoConfigurationCreatesOrderedCustomizersFromPropertiesAndJsonMapper() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testProperties", Map.of(
                    "spring.http.codecs.log-request-details", "true",
                    "spring.http.codecs.max-in-memory-size", "256KB")));
            context.register(JsonMapperConfiguration.class, CodecsAutoConfiguration.class);
            context.refresh();

            HttpCodecsProperties properties = context.getBean(HttpCodecsProperties.class);
            assertThat(properties.isLogRequestDetails()).isTrue();
            assertThat(properties.getMaxInMemorySize()).isEqualTo(DataSize.ofKilobytes(256));

            Map<String, CodecCustomizer> customizerBeans = context.getBeansOfType(CodecCustomizer.class);
            assertThat(customizerBeans).containsKeys("defaultCodecCustomizer", "jacksonCodecCustomizer");

            List<CodecCustomizer> customizers = new ArrayList<>(customizerBeans.values());
            AnnotationAwareOrderComparator.sort(customizers);
            CapturingCodecConfigurer configurer = new CapturingCodecConfigurer();
            customizers.forEach((customizer) -> customizer.customize(configurer));

            assertThat(configurer.defaultCodecs.loggingRequestDetailsEnabled).isTrue();
            assertThat(configurer.defaultCodecs.maxInMemorySize).isEqualTo((int) DataSize.ofKilobytes(256).toBytes());
            assertThat(configurer.defaultCodecs.jacksonJsonDecoder).isInstanceOf(JacksonJsonDecoder.class);
            assertThat(configurer.defaultCodecs.jacksonJsonEncoder).isInstanceOf(JacksonJsonEncoder.class);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void jacksonCodecCustomizerUsesApplicationJsonMapperForJsonPayloads() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(SortedJsonMapperConfiguration.class, CodecsAutoConfiguration.class);
            context.refresh();

            CapturingCodecConfigurer configurer = new CapturingCodecConfigurer();
            context.getBean("jacksonCodecCustomizer", CodecCustomizer.class).customize(configurer);

            Encoder<Object> encoder = (Encoder<Object>) configurer.defaultCodecs.jacksonJsonEncoder;
            Decoder<Object> decoder = (Decoder<Object>) configurer.defaultCodecs.jacksonJsonDecoder;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", "hello");
            payload.put("count", 2);

            DefaultDataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
            DataBuffer encoded = encoder.encodeValue(payload, bufferFactory, ResolvableType.forInstance(payload),
                    MediaType.APPLICATION_JSON, Collections.emptyMap());
            String json;
            try {
                json = encoded.toString(StandardCharsets.UTF_8);
            }
            finally {
                DataBufferUtils.release(encoded);
            }
            assertThat(json).isEqualTo("{\"count\":2,\"message\":\"hello\"}");

            DataBuffer input = bufferFactory.wrap(json.getBytes(StandardCharsets.UTF_8));
            Object decoded;
            try {
                decoded = decoder.decode(input, ResolvableType.forClass(Map.class), MediaType.APPLICATION_JSON,
                        Collections.emptyMap());
            }
            finally {
                DataBufferUtils.release(input);
            }
            assertThat(decoded).isInstanceOf(Map.class);
            Map<?, ?> decodedMap = (Map<?, ?>) decoded;
            assertThat(decodedMap.get("message")).isEqualTo("hello");
            assertThat(decodedMap.get("count")).isEqualTo(2);
        }
    }

    @Test
    void autoConfigurationBacksOffJacksonCustomizerWhenJsonMapperBeanIsAbsent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CodecsAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBean(HttpCodecsProperties.class).getMaxInMemorySize()).isNull();
            Map<String, CodecCustomizer> customizerBeans = context.getBeansOfType(CodecCustomizer.class);
            assertThat(customizerBeans).containsKey("defaultCodecCustomizer");
            assertThat(customizerBeans).doesNotContainKey("jacksonCodecCustomizer");

            CapturingCodecConfigurer configurer = new CapturingCodecConfigurer();
            customizerBeans.values().forEach((customizer) -> customizer.customize(configurer));

            assertThat(configurer.defaultCodecs.loggingRequestDetailsEnabled).isFalse();
            assertThat(configurer.defaultCodecs.maxInMemorySize).isNull();
            assertThat(configurer.defaultCodecs.jacksonJsonDecoder).isNull();
            assertThat(configurer.defaultCodecs.jacksonJsonEncoder).isNull();
        }
    }

    @Test
    void userCodecCustomizerCanOverrideAutoConfiguredDefaultCodecSettings() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testProperties", Map.of(
                    "spring.http.codecs.max-in-memory-size", "256KB")));
            context.register(UserCodecCustomizerConfiguration.class, CodecsAutoConfiguration.class);
            context.refresh();

            List<CodecCustomizer> customizers = context.getBean(CodecCustomizers.class).customizers;
            assertThat(customizers).hasSize(2);
            assertThat(customizers.get(1)).isInstanceOf(OverridingCodecCustomizer.class);

            CapturingCodecConfigurer configurer = new CapturingCodecConfigurer();
            customizers.forEach((customizer) -> customizer.customize(configurer));

            assertThat(configurer.defaultCodecs.maxInMemorySize).isEqualTo(512);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JsonMapperConfiguration {

        @Bean
        JsonMapper jsonMapper() {
            return new JsonMapper();
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class SortedJsonMapperConfiguration {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class UserCodecCustomizerConfiguration {

        @Bean
        CodecCustomizer overridingCodecCustomizer() {
            return new OverridingCodecCustomizer();
        }

        @Bean
        CodecCustomizers codecCustomizers(List<CodecCustomizer> customizers) {
            return new CodecCustomizers(customizers);
        }

    }

    private static final class OverridingCodecCustomizer implements CodecCustomizer {

        @Override
        public void customize(CodecConfigurer configurer) {
            configurer.defaultCodecs().maxInMemorySize(512);
        }

    }

    private static final class CodecCustomizers {

        private final List<CodecCustomizer> customizers;

        private CodecCustomizers(List<CodecCustomizer> customizers) {
            this.customizers = customizers;
        }

    }

    private static final class CapturingCodecConfigurer implements CodecConfigurer {

        private final CapturingDefaultCodecs defaultCodecs = new CapturingDefaultCodecs();

        @Override
        public DefaultCodecs defaultCodecs() {
            return this.defaultCodecs;
        }

        @Override
        public CustomCodecs customCodecs() {
            return null;
        }

        @Override
        public void registerDefaults(boolean registerDefaults) {
        }

        @Override
        public List<HttpMessageReader<?>> getReaders() {
            return Collections.emptyList();
        }

        @Override
        public List<HttpMessageWriter<?>> getWriters() {
            return Collections.emptyList();
        }

        @Override
        public CodecConfigurer clone() {
            throw new UnsupportedOperationException("Clone is not needed by these tests");
        }

    }

    private static final class CapturingDefaultCodecs implements CodecConfigurer.DefaultCodecs {

        private Decoder<?> jacksonJsonDecoder;

        private Encoder<?> jacksonJsonEncoder;

        private Integer maxInMemorySize;

        private Boolean loggingRequestDetailsEnabled;

        @Override
        public void jacksonJsonDecoder(Decoder<?> decoder) {
            this.jacksonJsonDecoder = decoder;
        }

        @Override
        public void jacksonJsonEncoder(Encoder<?> encoder) {
            this.jacksonJsonEncoder = encoder;
        }

        @Override
        public void gsonDecoder(Decoder<?> decoder) {
        }

        @Override
        public void gsonEncoder(Encoder<?> encoder) {
        }

        @Override
        public void jacksonSmileDecoder(Decoder<?> decoder) {
        }

        @Override
        public void jacksonSmileEncoder(Encoder<?> encoder) {
        }

        @Override
        public void jacksonCborDecoder(Decoder<?> decoder) {
        }

        @Override
        public void jacksonCborEncoder(Encoder<?> encoder) {
        }

        @Override
        public void jacksonXmlDecoder(Decoder<?> decoder) {
        }

        @Override
        public void jacksonXmlEncoder(Encoder<?> encoder) {
        }

        @Override
        public void protobufDecoder(Decoder<?> decoder) {
        }

        @Override
        public void protobufEncoder(Encoder<?> encoder) {
        }

        @Override
        public void jaxb2Decoder(Decoder<?> decoder) {
        }

        @Override
        public void jaxb2Encoder(Encoder<?> encoder) {
        }

        @Override
        public void kotlinSerializationCborDecoder(Decoder<?> decoder) {
        }

        @Override
        public void kotlinSerializationCborEncoder(Encoder<?> encoder) {
        }

        @Override
        public void kotlinSerializationJsonDecoder(Decoder<?> decoder) {
        }

        @Override
        public void kotlinSerializationJsonEncoder(Encoder<?> encoder) {
        }

        @Override
        public void kotlinSerializationProtobufDecoder(Decoder<?> decoder) {
        }

        @Override
        public void kotlinSerializationProtobufEncoder(Encoder<?> encoder) {
        }

        @Override
        public void configureDefaultCodec(Consumer<Object> codecConsumer) {
        }

        @Override
        public void maxInMemorySize(int byteCount) {
            this.maxInMemorySize = byteCount;
        }

        @Override
        public void enableLoggingRequestDetails(boolean enable) {
            this.loggingRequestDetailsEnabled = enable;
        }

        @Override
        public CodecConfigurer.MultipartCodecs multipartCodecs() {
            return null;
        }

        @Override
        public void multipartReader(HttpMessageReader<?> reader) {
        }

    }

}
