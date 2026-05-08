/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_jaxrs.jackson_jaxrs_base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NoContentException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.Version;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.jaxrs.annotation.JacksonFeatures;
import tools.jackson.jaxrs.base.ProviderBase;
import tools.jackson.jaxrs.cfg.AnnotationBundleKey;
import tools.jackson.jaxrs.cfg.EndpointConfigBase;
import tools.jackson.jaxrs.cfg.JaxRSFeature;
import tools.jackson.jaxrs.cfg.MapperConfiguratorBase;
import tools.jackson.jaxrs.cfg.ObjectReaderInjector;
import tools.jackson.jaxrs.cfg.ObjectReaderModifier;
import tools.jackson.jaxrs.cfg.ObjectWriterInjector;
import tools.jackson.jaxrs.cfg.ObjectWriterModifier;
import tools.jackson.jaxrs.util.ClassKey;

public class Jackson_jaxrs_baseTest {
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];

    @Test
    void jaxRsFeatureMasksCollectOnlyDefaults() {
        int defaults = JaxRSFeature.collectDefaults();

        assertThat(JaxRSFeature.ALLOW_EMPTY_INPUT.enabledIn(defaults)).isTrue();
        assertThat(JaxRSFeature.READ_FULL_STREAM.enabledIn(defaults)).isTrue();
        assertThat(JaxRSFeature.CACHE_ENDPOINT_READERS.enabledIn(defaults)).isTrue();
        assertThat(JaxRSFeature.CACHE_ENDPOINT_WRITERS.enabledIn(defaults)).isTrue();
        assertThat(JaxRSFeature.ADD_NO_SNIFF_HEADER.enabledIn(defaults)).isFalse();
        assertThat(JaxRSFeature.DYNAMIC_OBJECT_MAPPER_LOOKUP.enabledIn(defaults)).isFalse();
        assertThat(JaxRSFeature.MATCH_ALL_IF_NO_MEDIA_TYPE.enabledIn(defaults)).isFalse();
        assertThat(JaxRSFeature.values())
                .allSatisfy(feature -> assertThat(feature.enabledIn(feature.getMask())).isTrue());
    }

    @Test
    void classKeyTracksIdentityAndCanBeReset() {
        ClassKey stringKey = new ClassKey(String.class);
        ClassKey sameStringKey = new ClassKey(String.class);
        ClassKey integerKey = new ClassKey(Integer.class);
        ClassKey reusableKey = new ClassKey();

        reusableKey.reset(String.class);
        assertThat(reusableKey).isEqualTo(stringKey).hasSameHashCodeAs(stringKey);
        assertThat(stringKey).isEqualTo(sameStringKey);
        assertThat(stringKey).isNotEqualTo(integerKey);
        assertThat(stringKey.compareTo(integerKey))
                .isEqualTo(String.class.getName().compareTo(Integer.class.getName()));
        assertThat(stringKey.toString()).isEqualTo(String.class.getName());

        reusableKey.reset(Integer.class);
        assertThat(reusableKey).isEqualTo(integerKey).isNotEqualTo(stringKey);
    }

    @Test
    void annotationBundleKeyUsesTypeAnnotationsAndImmutableCopies() {
        Annotation first = new NamedAnnotationLiteral("first");
        Annotation second = new NamedAnnotationLiteral("second");
        Annotation[] annotations = {first, second};
        AnnotationBundleKey original = new AnnotationBundleKey(annotations, Map.class);
        AnnotationBundleKey immutable = original.immutableKey();
        AnnotationBundleKey equalKey = new AnnotationBundleKey(new Annotation[] {first, second}, Map.class);

        assertThat(original).isEqualTo(equalKey).hasSameHashCodeAs(equalKey);
        assertThat(immutable).isEqualTo(original);
        assertThat(original.toString())
                .contains("Annotations: 2")
                .contains(Map.class.getName());

        annotations[0] = new NamedAnnotationLiteral("changed");
        assertThat(original).isNotEqualTo(equalKey);
        assertThat(immutable).isEqualTo(equalKey);
        assertThat(new AnnotationBundleKey(NO_ANNOTATIONS, Map.class))
                .isEqualTo(new AnnotationBundleKey(null, Map.class));
        assertThat(original).isNotEqualTo(new AnnotationBundleKey(new Annotation[] {first, second}, String.class));
    }

    @Test
    void mapperConfiguratorBuildsAndReusesConfiguredMappers() {
        TestMapperConfigurator configurator = new TestMapperConfigurator(null);
        configurator.configure(SerializationFeature.INDENT_OUTPUT, true);
        configurator.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JsonMapper defaultMapper = configurator.getDefaultMapper();

        assertThat(defaultMapper).isSameAs(configurator.getDefaultMapper());
        assertThat(defaultMapper.isEnabled(SerializationFeature.INDENT_OUTPUT)).isTrue();
        assertThat(defaultMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(configurator.getConfiguredMapper()).isNull();

        JsonMapper explicitMapper = JsonMapper.builder()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
        configurator.setMapper(explicitMapper);
        assertThat(configurator.getConfiguredMapper()).isSameAs(explicitMapper);
        assertThat(configurator.getDefaultMapper()).isSameAs(defaultMapper);
    }

    @Test
    void endpointConfigAppliesFeatureViewAndRootAnnotations() throws JacksonException {
        JacksonFeaturesLiteral features = new JacksonFeaturesLiteral(
                new DeserializationFeature[] {DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY},
                new DeserializationFeature[] {DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES},
                new SerializationFeature[] {SerializationFeature.INDENT_OUTPUT},
                new SerializationFeature[] {SerializationFeature.FAIL_ON_EMPTY_BEANS});
        Annotation[] annotations = {
                new JsonViewLiteral(PublicView.class),
                new JsonRootNameLiteral("payload"),
                features
        };
        JsonMapper mapper = new JsonMapper();

        TestEndpointConfig reading = TestEndpointConfig.forReading(mapper.reader(), annotations);
        TestEndpointConfig writing = TestEndpointConfig.forWriting(mapper.writer(), annotations);

        assertThat(reading.getActiveView()).isEqualTo(PublicView.class);
        assertThat(reading.getRootName()).isEqualTo("payload");
        assertThat(reading.getReader().isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)).isTrue();
        assertThat(reading.getReader().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(writing.getActiveView()).isEqualTo(PublicView.class);
        assertThat(writing.getRootName()).isEqualTo("payload");
        assertThat(writing.getWriter().isEnabled(SerializationFeature.INDENT_OUTPUT)).isTrue();
        assertThat(writing.getWriter().isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)).isFalse();
        assertThat(writing.modifyBeforeWrite(Map.of("key", "value"))).isEqualTo(Map.of("key", "value"));
    }

    @Test
    void endpointConfigExpandsJacksonAnnotationBundles() {
        TestEndpointConfig config = TestEndpointConfig.forWriting(
                new JsonMapper().writer(),
                new Annotation[] {new BundledRootNameLiteral()});

        assertThat(config.getRootName()).isEqualTo("bundled");
    }

    @Test
    void endpointConfigRequiresInitializedReaderAndWriter() {
        TestEndpointConfig config = new TestEndpointConfig(new JsonMapper().serializationConfig());

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(config::getReader);
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(config::getWriter);
    }

    @Test
    void providerReadableAndWriteableHonorMediaTypesAndUntouchables() {
        TestProvider provider = new TestProvider();

        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE))
                .isTrue();
        assertThat(provider.isWriteable(Map.class, Map.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE))
                .isTrue();
        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS, MediaType.TEXT_PLAIN_TYPE))
                .isFalse();
        assertThat(provider.isWriteable(Map.class, Map.class, NO_ANNOTATIONS, MediaType.TEXT_PLAIN_TYPE))
                .isFalse();
        assertThat(provider.isReadable(String.class, String.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE))
                .isFalse();
        assertThat(provider.isWriteable(OutputStream.class, OutputStream.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE))
                .isFalse();
        assertThat(provider.isWriteable(Response.class, Response.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE))
                .isFalse();
        assertThat(provider.isWriteable(StreamingOutput.class, StreamingOutput.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE))
                .isFalse();
        assertThat(provider.isReadable(InputStream.class, InputStream.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE))
                .isFalse();
        assertThat(provider.isReadable(Reader.class, Reader.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE))
                .isFalse();
        assertThat(provider.isWriteable(Writer.class, Writer.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE))
                .isFalse();

        provider.removeUntouchable(String.class);
        assertThat(provider.isReadable(String.class, String.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE))
                .isTrue();
        assertThat(provider.isWriteable(String.class, String.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE))
                .isTrue();

        provider.addUntouchable(CharSequence.class);
        assertThat(provider.isReadable(StringBuilder.class, StringBuilder.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE))
                .isFalse();
        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS, null)).isFalse();
        provider.enable(JaxRSFeature.MATCH_ALL_IF_NO_MEDIA_TYPE);
        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS, null)).isTrue();
    }

    @Test
    void providerLocatesMappersInConfiguredOrder() {
        JsonMapper configuredMapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        JsonMapper providerMapper = JsonMapper.builder().disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
        TestProvider provider = new TestProvider();
        provider.mapperFromProvider = providerMapper;

        assertThat(provider.locateMapper(Map.class, MediaType.APPLICATION_JSON_TYPE)).isSameAs(providerMapper);
        provider.setMapper(configuredMapper);
        assertThat(provider.locateMapper(Map.class, MediaType.APPLICATION_JSON_TYPE)).isSameAs(configuredMapper);

        provider.enable(JaxRSFeature.DYNAMIC_OBJECT_MAPPER_LOOKUP);
        assertThat(provider.locateMapper(Map.class, MediaType.APPLICATION_JSON_TYPE)).isSameAs(providerMapper);
        provider.mapperFromProvider = null;
        assertThat(provider.locateMapper(Map.class, MediaType.APPLICATION_JSON_TYPE)).isSameAs(configuredMapper);
    }

    @Test
    void providerReadsWritesHeadersAndCachesEndpointConfigurations() throws Exception {
        TestProvider provider = new TestProvider();
        provider.enable(JaxRSFeature.ADD_NO_SNIFF_HEADER);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", "graal");
        value.put("count", 3);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        provider.writeTo(value, Map.class, Map.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE, headers, output);
        provider.writeTo(value, Map.class, Map.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(), new ByteArrayOutputStream());

        assertThat(headers.getFirst(ProviderBase.HEADER_CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        assertThat(json(output)).contains("\"name\":\"graal\"").contains("\"count\":3");
        assertThat(provider.writingConfigurations).isEqualTo(1);

        Object first = read(provider, "{\"name\":\"graal\"}");
        Object second = read(provider, "{\"name\":\"native\"}");

        assertThat(first).isInstanceOf(Map.class);
        assertThat(first).isEqualTo(Map.of("name", "graal"));
        assertThat(second).isEqualTo(Map.of("name", "native"));
        assertThat(provider.readingConfigurations).isEqualTo(1);
        assertThat(provider.getSize(value, Map.class, Map.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE))
                .isEqualTo(-1L);
    }

    @Test
    void providerCanDisableEndpointConfigurationCaches() throws Exception {
        TestProvider provider = new TestProvider();
        provider.disable(JaxRSFeature.CACHE_ENDPOINT_READERS, JaxRSFeature.CACHE_ENDPOINT_WRITERS);
        Map<String, Object> value = Map.of("cache", "disabled");

        provider.writeTo(value, Map.class, Map.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(), new ByteArrayOutputStream());
        provider.writeTo(value, Map.class, Map.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(), new ByteArrayOutputStream());
        read(provider, "{\"cache\":\"disabled\"}");
        read(provider, "{\"cache\":\"disabled\"}");

        assertThat(provider.writingConfigurations).isEqualTo(2);
        assertThat(provider.readingConfigurations).isEqualTo(2);
    }

    @Test
    void providerHandlesEmptyInputAccordingToFeatureFlag() throws Exception {
        TestProvider provider = new TestProvider();

        assertThat(read(provider, "")).isNull();

        provider.disable(JaxRSFeature.ALLOW_EMPTY_INPUT);
        assertThatThrownBy(() -> read(provider, ""))
                .isInstanceOf(NoContentException.class)
                .hasMessageContaining("No content");
    }

    @Test
    void providerCanReturnParserDirectlyFromRead() throws Exception {
        TestProvider provider = new TestProvider();
        JsonParser parser = (JsonParser) provider.readFrom(
                parserClass(),
                JsonParser.class,
                NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                new ByteArrayInputStream("{\"token\":true}".getBytes(StandardCharsets.UTF_8)));

        assertThat(parser.currentToken()).isNotNull();
        assertThat(parser.nextName()).isEqualTo("token");
        parser.close();
    }

    @Test
    void readerAndWriterInjectorsModifyOnlyCurrentOperation() throws Exception {
        TestProvider provider = new TestProvider();
        AtomicBoolean writerModifierCalled = new AtomicBoolean();
        AtomicBoolean readerModifierCalled = new AtomicBoolean();
        ObjectWriterModifier writerModifier = new ObjectWriterModifier() {
            @Override
            public ObjectWriter modify(EndpointConfigBase<?> endpoint, MultivaluedMap<String, Object> responseHeaders,
                    Object valueToWrite, ObjectWriter writer) {
                writerModifierCalled.set(true);
                assertThat(endpoint.getWriter()).isNotNull();
                assertThat(valueToWrite).isInstanceOf(Map.class);
                responseHeaders.add("X-Writer-Modifier", "called");
                return writer.withFeatures(SerializationFeature.INDENT_OUTPUT);
            }
        };
        ObjectReaderModifier readerModifier = new ObjectReaderModifier() {
            @Override
            public ObjectReader modify(EndpointConfigBase<?> endpoint, MultivaluedMap<String, String> requestHeaders,
                    JavaType valueType, ObjectReader reader, JsonParser parser) {
                readerModifierCalled.set(true);
                assertThat(endpoint.getReader()).isNotNull();
                assertThat(valueType.getRawClass()).isEqualTo(Map.class);
                assertThat(parser.currentToken()).isNotNull();
                return reader.withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            }
        };
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            ObjectWriterInjector.set(writerModifier);
            ObjectReaderInjector.set(readerModifier);
            provider.writeTo(Map.of("injected", true), Map.class, Map.class, NO_ANNOTATIONS,
                    MediaType.APPLICATION_JSON_TYPE, headers, output);
            Object readValue = read(provider, "{\"injected\":true}");

            assertThat(writerModifierCalled).isTrue();
            assertThat(readerModifierCalled).isTrue();
            assertThat(headers.getFirst("X-Writer-Modifier")).isEqualTo("called");
            assertThat(json(output)).contains(System.lineSeparator());
            assertThat(readValue).isEqualTo(Map.of("injected", true));
            assertThat(ObjectWriterInjector.get()).isNull();
            assertThat(ObjectReaderInjector.get()).isNull();
        } finally {
            ObjectWriterInjector.getAndClear();
            ObjectReaderInjector.getAndClear();
        }
    }

    @Test
    void injectorAccessorsUseThreadLocalState() {
        ObjectWriterModifier writerModifier = new ObjectWriterModifier() {
            @Override
            public ObjectWriter modify(EndpointConfigBase<?> endpoint, MultivaluedMap<String, Object> responseHeaders,
                    Object valueToWrite, ObjectWriter writer) {
                return writer;
            }
        };
        ObjectReaderModifier readerModifier = new ObjectReaderModifier() {
            @Override
            public ObjectReader modify(EndpointConfigBase<?> endpoint, MultivaluedMap<String, String> requestHeaders,
                    JavaType valueType, ObjectReader reader, JsonParser parser) {
                return reader;
            }
        };

        ObjectWriterInjector.set(writerModifier);
        ObjectReaderInjector.set(readerModifier);

        assertThat(ObjectWriterInjector.get()).isSameAs(writerModifier);
        assertThat(ObjectReaderInjector.get()).isSameAs(readerModifier);
        assertThat(ObjectWriterInjector.getAndClear()).isSameAs(writerModifier);
        assertThat(ObjectReaderInjector.getAndClear()).isSameAs(readerModifier);
        assertThat(ObjectWriterInjector.get()).isNull();
        assertThat(ObjectReaderInjector.get()).isNull();
    }

    private static Object read(TestProvider provider, String json) throws Exception {
        return provider.readFrom(mapClass(), Map.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String json(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> mapClass() {
        return (Class<Object>) (Class<?>) Map.class;
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> parserClass() {
        return (Class<Object>) (Class<?>) JsonParser.class;
    }

    private static final class TestProvider extends ProviderBase<TestProvider, JsonMapper, TestEndpointConfig,
            TestMapperConfigurator> {
        private JsonMapper mapperFromProvider;
        private int readingConfigurations;
        private int writingConfigurations;

        private TestProvider() {
            super(new TestMapperConfigurator(null));
        }

        @Override
        public Version version() {
            return JsonMapper.shared().version();
        }

        @Override
        protected boolean hasMatchingMediaType(MediaType mediaType) {
            if (mediaType == null) {
                return isEnabled(JaxRSFeature.MATCH_ALL_IF_NO_MEDIA_TYPE);
            }
            if ("json".equals(mediaType.getSubtype())) {
                return true;
            }
            return mediaType.getSubtype().endsWith("+json");
        }

        @Override
        protected JsonMapper _locateMapperViaProvider(Class<?> type, MediaType mediaType) {
            return mapperFromProvider;
        }

        @Override
        protected TestEndpointConfig _configForReading(ObjectReader reader, Annotation[] annotations) {
            readingConfigurations++;
            return TestEndpointConfig.forReading(reader, annotations);
        }

        @Override
        protected TestEndpointConfig _configForWriting(ObjectWriter writer, Annotation[] annotations) {
            writingConfigurations++;
            return TestEndpointConfig.forWriting(writer, annotations);
        }
    }

    private static final class TestMapperConfigurator
            extends MapperConfiguratorBase<TestMapperConfigurator, JsonMapper> {
        private TestMapperConfigurator(JsonMapper mapper) {
            super(mapper, null);
        }

        @Override
        protected MapperBuilder<?, ?> mapperBuilder() {
            return JsonMapper.builder();
        }
    }

    private static final class TestEndpointConfig extends EndpointConfigBase<TestEndpointConfig> {
        private TestEndpointConfig(MapperConfig<?> config) {
            super(config);
        }

        private static TestEndpointConfig forReading(ObjectReader reader, Annotation[] annotations) {
            return new TestEndpointConfig(reader.getConfig())
                    .add(annotations, false)
                    .initReader(reader);
        }

        private static TestEndpointConfig forWriting(ObjectWriter writer, Annotation[] annotations) {
            return new TestEndpointConfig(writer.getConfig())
                    .add(annotations, true)
                    .initWriter(writer);
        }

        @Override
        public Object modifyBeforeWrite(Object value) {
            return value;
        }
    }

    private static final class JsonViewLiteral implements JsonView {
        private final Class<?>[] value;

        private JsonViewLiteral(Class<?>... value) {
            this.value = value;
        }

        @Override
        public Class<?>[] value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return JsonView.class;
        }
    }

    private static final class JsonRootNameLiteral implements JsonRootName {
        private final String value;

        private JsonRootNameLiteral(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public String namespace() {
            return "";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return JsonRootName.class;
        }
    }

    private static final class JacksonFeaturesLiteral implements JacksonFeatures {
        private final DeserializationFeature[] deserializationEnable;
        private final DeserializationFeature[] deserializationDisable;
        private final SerializationFeature[] serializationEnable;
        private final SerializationFeature[] serializationDisable;

        private JacksonFeaturesLiteral(DeserializationFeature[] deserializationEnable,
                DeserializationFeature[] deserializationDisable, SerializationFeature[] serializationEnable,
                SerializationFeature[] serializationDisable) {
            this.deserializationEnable = deserializationEnable;
            this.deserializationDisable = deserializationDisable;
            this.serializationEnable = serializationEnable;
            this.serializationDisable = serializationDisable;
        }

        @Override
        public DeserializationFeature[] deserializationEnable() {
            return deserializationEnable;
        }

        @Override
        public DeserializationFeature[] deserializationDisable() {
            return deserializationDisable;
        }

        @Override
        public SerializationFeature[] serializationEnable() {
            return serializationEnable;
        }

        @Override
        public SerializationFeature[] serializationDisable() {
            return serializationDisable;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return JacksonFeatures.class;
        }
    }

    private static final class NamedAnnotationLiteral implements Annotation {
        private final String name;

        private NamedAnnotationLiteral(String name) {
            this.name = name;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return NamedAnnotation.class;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof NamedAnnotationLiteral)) {
                return false;
            }
            NamedAnnotationLiteral that = (NamedAnnotationLiteral) other;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    private static final class BundledRootNameLiteral implements Annotation {
        @Override
        public Class<? extends Annotation> annotationType() {
            return BundledRootName.class;
        }
    }

    private interface PublicView {
    }

    private @interface NamedAnnotation {
    }

    @JacksonAnnotationsInside
    @JsonRootName("bundled")
    @Retention(RetentionPolicy.RUNTIME)
    private @interface BundledRootName {
    }
}
