/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jakarta_rs.jackson_jakarta_rs_base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.jakarta.rs.annotation.JacksonFeatures;
import com.fasterxml.jackson.jakarta.rs.base.ProviderBase;
import com.fasterxml.jackson.jakarta.rs.base.util.ClassKey;
import com.fasterxml.jackson.jakarta.rs.base.util.EndpointAsBeanProperty;
import com.fasterxml.jackson.jakarta.rs.cfg.AnnotationBundleKey;
import com.fasterxml.jackson.jakarta.rs.cfg.Annotations;
import com.fasterxml.jackson.jakarta.rs.cfg.EndpointConfigBase;
import com.fasterxml.jackson.jakarta.rs.cfg.JakartaRSFeature;
import com.fasterxml.jackson.jakarta.rs.cfg.MapperConfiguratorBase;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectReaderInjector;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectReaderModifier;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectWriterInjector;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectWriterModifier;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Jackson_jakarta_rs_baseTest {
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
    private static final MediaType JSON = MediaType.APPLICATION_JSON_TYPE;

    @BeforeEach
    void clearInjectorsBeforeTest() {
        ObjectReaderInjector.getAndClear();
        ObjectWriterInjector.getAndClear();
    }

    @AfterEach
    void clearInjectorsAfterTest() {
        ObjectReaderInjector.getAndClear();
        ObjectWriterInjector.getAndClear();
    }

    @Test
    void providerReadabilityAndWritabilityHonorMediaTypesAndUntouchables() {
        TestProvider provider = new TestProvider(new ObjectMapper());

        assertThat(provider.isReadable(User.class, User.class, NO_ANNOTATIONS, JSON)).isTrue();
        assertThat(provider.isWriteable(User.class, User.class, NO_ANNOTATIONS, JSON)).isTrue();
        assertThat(provider.isReadable(User.class, User.class, NO_ANNOTATIONS, MediaType.TEXT_PLAIN_TYPE)).isFalse();
        assertThat(provider.isWriteable(User.class, User.class, NO_ANNOTATIONS, MediaType.TEXT_PLAIN_TYPE)).isFalse();

        assertThat(provider.isReadable(String.class, String.class, NO_ANNOTATIONS, JSON)).isFalse();
        assertThat(provider.isWriteable(String.class, String.class, NO_ANNOTATIONS, JSON)).isFalse();
        provider.removeUntouchable(String.class);
        assertThat(provider.isReadable(String.class, String.class, NO_ANNOTATIONS, JSON)).isTrue();
        assertThat(provider.isWriteable(String.class, String.class, NO_ANNOTATIONS, JSON)).isTrue();

        provider.addUntouchable(User.class);
        assertThat(provider.isReadable(User.class, User.class, NO_ANNOTATIONS, JSON)).isFalse();
        assertThat(provider.isWriteable(User.class, User.class, NO_ANNOTATIONS, JSON)).isFalse();
        provider.removeUntouchable(User.class);
        assertThat(provider.isReadable(User.class, User.class, NO_ANNOTATIONS, JSON)).isTrue();
        assertThat(provider.isWriteable(User.class, User.class, NO_ANNOTATIONS, JSON)).isTrue();

        assertThat(provider.isReadable(InputStream.class, InputStream.class, NO_ANNOTATIONS, JSON)).isFalse();
        assertThat(provider.isWriteable(OutputStream.class, OutputStream.class, NO_ANNOTATIONS, JSON)).isFalse();

        provider.checkCanDeserialize(true);
        assertThat(provider.isReadable(JsonParser.class, JsonParser.class, NO_ANNOTATIONS, JSON)).isTrue();
    }

    @Test
    void writeToAppliesEndpointAnnotationsHeadersAndWriterModifier() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .build();
        TestProvider provider = new TestProvider(mapper);
        provider.enable(JakartaRSFeature.ADD_NO_SNIFF_HEADER);

        AtomicReference<String> modifierRootName = new AtomicReference<>();
        ObjectWriterInjector.set(new ObjectWriterModifier() {
            @Override
            public ObjectWriter modify(EndpointConfigBase<?> endpoint,
                    MultivaluedMap<String, Object> responseHeaders,
                    Object valueToWrite, ObjectWriter writer, JsonGenerator generator) {
                modifierRootName.set(endpoint.getRootName());
                responseHeaders.add("X-Writer-Modifier", valueToWrite.getClass().getSimpleName());
                return writer;
            }
        });

        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();
        Annotation[] annotations = new Annotation[] {
                new JsonViewAnnotation(PublicView.class),
                new JsonRootNameAnnotation("user"),
                new JacksonFeaturesAnnotation(
                        new DeserializationFeature[0],
                        new DeserializationFeature[0],
                        new SerializationFeature[] {
                                SerializationFeature.INDENT_OUTPUT,
                                SerializationFeature.WRAP_ROOT_VALUE
                        },
                        new SerializationFeature[0])
        };

        provider.writeTo(new User("shown", "hidden"), User.class, User.class, annotations, JSON, headers, output);

        String json = output.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("\n");
        assertThat(json).contains("\"user\"");
        assertThat(json).contains("\"visible\" : \"shown\"");
        assertThat(json).doesNotContain("hidden");
        assertThat(output.closed()).isFalse();
        assertThat(headers.getFirst(ProviderBase.HEADER_CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Writer-Modifier")).isEqualTo("User");
        assertThat(modifierRootName).hasValue("user");
        assertThat(ObjectWriterInjector.get()).isNull();
    }

    @Test
    void readFromAppliesEndpointAnnotationsAndReaderModifier() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .build();
        TestProvider provider = new TestProvider(mapper);
        AtomicReference<String> rootNameSeenByModifier = new AtomicReference<>();
        AtomicReference<Class<?>> activeViewSeenByModifier = new AtomicReference<>();
        AtomicReference<JavaType> resultTypeSeenByModifier = new AtomicReference<>();
        ObjectReaderInjector.set(new ObjectReaderModifier() {
            @Override
            public ObjectReader modify(EndpointConfigBase<?> endpoint,
                    MultivaluedMap<String, String> httpHeaders,
                    JavaType resultType,
                    ObjectReader reader,
                    JsonParser parser) {
                rootNameSeenByModifier.set(endpoint.getRootName());
                activeViewSeenByModifier.set(endpoint.getActiveView());
                resultTypeSeenByModifier.set(resultType);
                return reader;
            }
        });

        Annotation[] annotations = new Annotation[] {
                new JsonViewAnnotation(PublicView.class),
                new JsonRootNameAnnotation("user"),
                new JacksonFeaturesAnnotation(
                        new DeserializationFeature[] {DeserializationFeature.UNWRAP_ROOT_VALUE },
                        new DeserializationFeature[] {DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES },
                        new SerializationFeature[0],
                        new SerializationFeature[0])
        };
        byte[] json = """
                {"user":{"visible":"shown","secret":"hidden","ignored":true}}
                """.getBytes(StandardCharsets.UTF_8);

        Object result = provider.readFrom(objectClass(User.class), User.class, annotations, JSON,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(json));

        assertThat(result).isInstanceOfSatisfying(User.class, user -> {
            assertThat(user.visible).isEqualTo("shown");
            assertThat(user.secret).isNull();
        });
        assertThat(rootNameSeenByModifier).hasValue("user");
        assertThat(activeViewSeenByModifier).hasValue(PublicView.class);
        assertThat(resultTypeSeenByModifier.get().getRawClass()).isEqualTo(User.class);
        assertThat(ObjectReaderInjector.get()).isNull();
    }

    @Test
    void defaultViewsApplyWhenEndpointHasNoViewAnnotation() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .build();
        TestProvider provider = new TestProvider(mapper);
        assertThat(provider.setDefaultReadView(PublicView.class)).isSameAs(provider);
        assertThat(provider.setDefaultWriteView(PublicView.class)).isSameAs(provider);

        CloseTrackingOutputStream output = new CloseTrackingOutputStream();
        provider.writeTo(new User("shown", "hidden"), User.class, User.class, NO_ANNOTATIONS, JSON,
                new MultivaluedHashMap<>(), output);

        String json = output.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"visible\":\"shown\"");
        assertThat(json).doesNotContain("secret", "hidden");

        byte[] input = """
                {"visible":"incoming","secret":"ignored"}
                """.getBytes(StandardCharsets.UTF_8);
        Object result = provider.readFrom(objectClass(User.class), User.class, NO_ANNOTATIONS, JSON,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(input));

        assertThat(result).isInstanceOfSatisfying(User.class, user -> {
            assertThat(user.visible).isEqualTo("incoming");
            assertThat(user.secret).isNull();
        });
    }

    @Test
    void readFromHandlesEmptyInputAccordingToFeatureConfiguration() throws Exception {
        TestProvider provider = new TestProvider(new ObjectMapper());

        Object emptyValue = provider.readFrom(objectClass(User.class), User.class, NO_ANNOTATIONS, JSON,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(new byte[0]));
        assertThat(emptyValue).isNull();

        provider.disable(JakartaRSFeature.ALLOW_EMPTY_INPUT);
        assertThatThrownBy(() -> provider.readFrom(objectClass(User.class), User.class, NO_ANNOTATIONS, JSON,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No content");
    }

    @Test
    void readFromConsumesFullStreamWhenFeatureIsEnabled() throws Exception {
        byte[] inputWithTrailingJson = "{\"visible\":\"first\"} {\"visible\":\"second\"}"
                .getBytes(StandardCharsets.UTF_8);
        TestProvider strictProvider = new TestProvider(new ObjectMapper());
        assertThat(strictProvider.isEnabled(JakartaRSFeature.READ_FULL_STREAM)).isTrue();

        assertThatThrownBy(() -> strictProvider.readFrom(objectClass(User.class), User.class, NO_ANNOTATIONS, JSON,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(inputWithTrailingJson)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Trailing token");

        TestProvider lenientProvider = new TestProvider(new ObjectMapper());
        lenientProvider.disable(JakartaRSFeature.READ_FULL_STREAM);
        Object result = lenientProvider.readFrom(objectClass(User.class), User.class, NO_ANNOTATIONS, JSON,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(inputWithTrailingJson));

        assertThat(result).isInstanceOfSatisfying(User.class, user -> {
            assertThat(user.visible).isEqualTo("first");
            assertThat(user.secret).isNull();
        });
    }

    @Test
    void endpointCachingAndDynamicMapperLookupAreConfigurable() throws Exception {
        ObjectMapper configuredMapper = new ObjectMapper();
        ObjectMapper providerMapper = new ObjectMapper();
        TestProvider provider = new TestProvider(configuredMapper);
        provider.setProviderMapper(providerMapper);

        assertThat(provider.locateMapper(User.class, JSON)).isSameAs(configuredMapper);
        provider.enable(JakartaRSFeature.DYNAMIC_OBJECT_MAPPER_LOOKUP);
        assertThat(provider.locateMapper(User.class, JSON)).isSameAs(providerMapper);
        provider.setProviderMapper(null);
        assertThat(provider.locateMapper(User.class, JSON)).isSameAs(configuredMapper);

        byte[] input = "{\"visible\":\"first\"}".getBytes(StandardCharsets.UTF_8);
        provider.readFrom(objectClass(User.class), User.class, NO_ANNOTATIONS, JSON,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(input));
        provider.readFrom(objectClass(User.class), User.class, NO_ANNOTATIONS, JSON,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(input));
        assertThat(provider.readConfigCalls()).isEqualTo(1);

        provider.disable(JakartaRSFeature.CACHE_ENDPOINT_READERS);
        provider.readFrom(objectClass(User.class), User.class, NO_ANNOTATIONS, JSON,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(input));
        provider.readFrom(objectClass(User.class), User.class, NO_ANNOTATIONS, JSON,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(input));
        assertThat(provider.readConfigCalls()).isEqualTo(3);
    }

    @Test
    void utilityKeysAndEndpointBeanPropertyExposeAnnotationState() {
        ClassKey stringKey = new ClassKey(String.class);
        ClassKey anotherStringKey = new ClassKey(String.class);
        ClassKey integerKey = new ClassKey(Integer.class);
        assertThat(stringKey).isEqualTo(anotherStringKey);
        assertThat(stringKey).isNotEqualTo(integerKey);
        assertThat(stringKey.compareTo(integerKey)).isPositive();
        integerKey.reset(String.class);
        assertThat(integerKey).isEqualTo(stringKey);
        assertThat(integerKey.toString()).isEqualTo(String.class.getName());

        Annotation first = new JsonRootNameAnnotation("first");
        Annotation second = new JsonRootNameAnnotation("second");
        Annotation[] annotations = new Annotation[] {first };
        AnnotationBundleKey mutableKey = new AnnotationBundleKey(annotations, User.class);
        AnnotationBundleKey immutableKey = mutableKey.immutableKey();
        assertThat(immutableKey).isEqualTo(mutableKey);
        annotations[0] = second;
        assertThat(immutableKey).isNotEqualTo(mutableKey);
        assertThat(immutableKey.toString()).contains("Annotations: 1", User.class.getName(), "copied: true");

        ObjectMapper mapper = new ObjectMapper();
        JavaType userType = mapper.constructType(User.class);
        EndpointAsBeanProperty property = new EndpointAsBeanProperty(
                EndpointAsBeanProperty.ENDPOINT_NAME, userType, new Annotation[] {first });
        assertThat(property.getName()).isEqualTo("Jakarta-RS/endpoint");
        assertThat(property
                .getAnnotation(JsonRootName.class).value()).isEqualTo("first");
        assertThat(property.withType(userType)).isSameAs(property);
        assertThat(property.withType(mapper.constructType(String.class))).isNotSameAs(property);
    }

    @Test
    void mapperConfiguratorAppliesAnnotationAndFeatureConfiguration() throws Exception {
        TestMapperConfigurator configurator = new TestMapperConfigurator(null);
        ObjectMapper defaultMapper = configurator.getDefaultMapper();
        assertThat(configurator.getDefaultMapper()).isSameAs(defaultMapper);
        assertThat(defaultMapper.getSerializationConfig().getAnnotationIntrospector())
                .isInstanceOf(JacksonAnnotationIntrospector.class);

        configurator.configure(SerializationFeature.INDENT_OUTPUT, true);
        configurator.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ObjectMapper configuredMapper = configurator.getConfiguredMapper();
        assertThat(configuredMapper.isEnabled(SerializationFeature.INDENT_OUTPUT)).isTrue();
        assertThat(configuredMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();

        configurator.setAnnotationsToUse(new Annotations[0]);
        assertThat(configuredMapper.writeValueAsString(new RenamedValue("test"))).contains("value");
        configurator.setAnnotationsToUse(new Annotations[] {Annotations.JACKSON });
        assertThat(configuredMapper.writeValueAsString(new AnotherRenamedValue("test"))).contains("renamed");
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<Object> objectClass(Class<T> type) {
        return (Class<Object>) type;
    }

    private interface PublicView {
    }

    private interface InternalView extends PublicView {
    }

    public static class User {
        @JsonView(PublicView.class)
        public String visible;

        @JsonView(InternalView.class)
        public String secret;

        public User() {
        }

        User(String visible, String secret) {
            this.visible = visible;
            this.secret = secret;
        }
    }

    public static class RenamedValue {
        @JsonProperty("renamed")
        public String value;

        public RenamedValue() {
        }

        RenamedValue(String value) {
            this.value = value;
        }
    }

    public static class AnotherRenamedValue {
        @JsonProperty("renamed")
        public String value;

        public AnotherRenamedValue() {
        }

        AnotherRenamedValue(String value) {
            this.value = value;
        }
    }

    private static final class TestProvider extends ProviderBase<TestProvider, ObjectMapper, TestEndpointConfig,
            TestMapperConfigurator> {
        private final AtomicInteger readConfigCalls = new AtomicInteger();
        private ObjectMapper providerMapper;

        private TestProvider(ObjectMapper mapper) {
            super(new TestMapperConfigurator(mapper));
        }

        private void setProviderMapper(ObjectMapper providerMapper) {
            this.providerMapper = providerMapper;
        }

        private int readConfigCalls() {
            return readConfigCalls.get();
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        @Override
        protected boolean hasMatchingMediaType(MediaType mediaType) {
            if (mediaType == null) {
                return true;
            }
            String subtype = mediaType.getSubtype();
            return mediaType.isCompatible(JSON) || subtype.endsWith("+json");
        }

        @Override
        protected ObjectMapper _locateMapperViaProvider(Class<?> type, MediaType mediaType) {
            return providerMapper;
        }

        @Override
        protected TestEndpointConfig _configForReading(ObjectReader reader, Annotation[] annotations) {
            readConfigCalls.incrementAndGet();
            return TestEndpointConfig.forReading(reader, annotations);
        }

        @Override
        protected TestEndpointConfig _configForWriting(ObjectWriter writer, Annotation[] annotations) {
            return TestEndpointConfig.forWriting(writer, annotations);
        }
    }

    private static final class TestEndpointConfig extends EndpointConfigBase<TestEndpointConfig> {
        private TestEndpointConfig(ObjectReader reader) {
            super(reader.getConfig());
        }

        private TestEndpointConfig(ObjectWriter writer) {
            super(writer.getConfig());
        }

        private static TestEndpointConfig forReading(ObjectReader reader, Annotation[] annotations) {
            TestEndpointConfig config = new TestEndpointConfig(reader);
            config.add(annotations, false);
            return config.initReader(reader);
        }

        private static TestEndpointConfig forWriting(ObjectWriter writer, Annotation[] annotations) {
            TestEndpointConfig config = new TestEndpointConfig(writer);
            config.add(annotations, true);
            return config.initWriter(writer);
        }

        @Override
        public Object modifyBeforeWrite(Object value) {
            return value;
        }
    }

    private static final class TestMapperConfigurator extends MapperConfiguratorBase<TestMapperConfigurator,
            ObjectMapper> {
        private TestMapperConfigurator(ObjectMapper mapper) {
            super(mapper, new Annotations[] {Annotations.JACKSON });
        }

        @Override
        public ObjectMapper getConfiguredMapper() {
            return _mapper;
        }

        @Override
        public ObjectMapper getDefaultMapper() {
            ObjectMapper mapper = _defaultMapper;
            if (mapper == null) {
                mapper = new ObjectMapper();
                _setAnnotations(mapper, _defaultAnnotationsToUse);
                _defaultMapper = mapper;
            }
            return mapper;
        }

        @Override
        protected ObjectMapper mapper() {
            ObjectMapper mapper = _mapper;
            if (mapper == null) {
                mapper = new ObjectMapper();
                _setAnnotations(mapper, _defaultAnnotationsToUse);
                _mapper = mapper;
            }
            return mapper;
        }

        @Override
        protected AnnotationIntrospector _resolveIntrospectors(Annotations[] annotationsToUse) {
            AnnotationIntrospector introspector = null;
            for (Annotations annotation : annotationsToUse) {
                if (annotation == Annotations.JACKSON) {
                    AnnotationIntrospector jacksonIntrospector = new JacksonAnnotationIntrospector();
                    introspector = introspector == null
                            ? jacksonIntrospector
                            : AnnotationIntrospector.pair(introspector, jacksonIntrospector);
                } else if (annotation == Annotations.JAKARTA_XML_BIND) {
                    throw new IllegalArgumentException(
                            "Jakarta XML Bind annotations are not part of this test classpath");
                }
            }
            return introspector == null ? AnnotationIntrospector.nopInstance() : introspector;
        }
    }

    private static final class CloseTrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean closed() {
            return closed;
        }
    }

    private static final class JsonRootNameAnnotation implements JsonRootName {
        private final String value;

        private JsonRootNameAnnotation(String value) {
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

    private static final class JsonViewAnnotation implements JsonView {
        private final Class<?>[] value;

        private JsonViewAnnotation(Class<?>... value) {
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

    private static final class JacksonFeaturesAnnotation implements JacksonFeatures {
        private final DeserializationFeature[] deserializationEnable;
        private final DeserializationFeature[] deserializationDisable;
        private final SerializationFeature[] serializationEnable;
        private final SerializationFeature[] serializationDisable;

        private JacksonFeaturesAnnotation(DeserializationFeature[] deserializationEnable,
                DeserializationFeature[] deserializationDisable,
                SerializationFeature[] serializationEnable,
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
}
