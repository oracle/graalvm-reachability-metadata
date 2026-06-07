/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jaxrs.jackson_jaxrs_base;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.jaxrs.annotation.JacksonFeatures;
import com.fasterxml.jackson.jaxrs.base.JsonMappingExceptionMapper;
import com.fasterxml.jackson.jaxrs.base.JsonParseExceptionMapper;
import com.fasterxml.jackson.jaxrs.base.NoContentExceptionSupplier;
import com.fasterxml.jackson.jaxrs.base.ProviderBase;
import com.fasterxml.jackson.jaxrs.base.nocontent.JaxRS1NoContentExceptionSupplier;
import com.fasterxml.jackson.jaxrs.base.nocontent.JaxRS2NoContentExceptionSupplier;
import com.fasterxml.jackson.jaxrs.cfg.AnnotationBundleKey;
import com.fasterxml.jackson.jaxrs.cfg.Annotations;
import com.fasterxml.jackson.jaxrs.cfg.EndpointConfigBase;
import com.fasterxml.jackson.jaxrs.cfg.JaxRSFeature;
import com.fasterxml.jackson.jaxrs.cfg.MapperConfiguratorBase;
import com.fasterxml.jackson.jaxrs.cfg.ObjectReaderInjector;
import com.fasterxml.jackson.jaxrs.cfg.ObjectReaderModifier;
import com.fasterxml.jackson.jaxrs.cfg.ObjectWriterInjector;
import com.fasterxml.jackson.jaxrs.cfg.ObjectWriterModifier;
import com.fasterxml.jackson.jaxrs.util.ClassKey;
import com.fasterxml.jackson.jaxrs.util.EndpointAsBeanProperty;
import com.fasterxml.jackson.jaxrs.util.LRUMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProviderBaseTest {
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];

    @AfterEach
    public void clearThreadLocalModifiers() {
        ObjectReaderInjector.getAndClear();
        ObjectWriterInjector.getAndClear();
    }

    @Test
    public void providerReadsAndWritesJsonThroughJaxRsBodyMethods() throws IOException {
        TestProvider provider = new TestProvider();
        provider.enable(JaxRSFeature.ADD_NO_SNIFF_HEADER);
        provider.enable(SerializationFeature.INDENT_OUTPUT);
        provider.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        provider.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

        assertThat(provider.isReadable(Message.class, Message.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE)).isTrue();
        assertThat(provider.isWriteable(Message.class, Message.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE)).isTrue();
        assertThat(provider.isReadable(Message.class, Message.class, NO_ANNOTATIONS,
                MediaType.TEXT_PLAIN_TYPE)).isFalse();
        assertThat(provider.getSize(new Message("ignored", 0), Message.class, Message.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE)).isEqualTo(-1L);

        MultivaluedMap<String, Object> responseHeaders = new MultivaluedHashMap<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        provider.writeTo(new Message("hello", 3), Message.class, Message.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, responseHeaders, output);

        assertThat(responseHeaders.getFirst(ProviderBase.HEADER_CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        String json = output.toString(StandardCharsets.UTF_8.name());
        assertThat(json).contains("\"text\" : \"hello\"");
        assertThat(json).contains("\"count\" : 3");

        ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        Object value = provider.readFrom(objectClass(Message.class), Message.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, new MultivaluedHashMap<>(), input);

        assertThat(value).isInstanceOf(Message.class);
        Message message = (Message) value;
        assertThat(message.text).isEqualTo("hello");
        assertThat(message.count).isEqualTo(3);
    }

    @Test
    public void providerUsesDefaultJsonViewsWhenNoEndpointViewAnnotationIsPresent() throws IOException {
        TestProvider writerProvider = new TestProvider();
        assertThat(writerProvider.setDefaultWriteView(PublicView.class)).isSameAs(writerProvider);

        MultivaluedMap<String, Object> responseHeaders = new MultivaluedHashMap<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writerProvider.writeTo(new ScopedMessage("visible", "hidden"), ScopedMessage.class, ScopedMessage.class,
                NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE, responseHeaders, output);

        String json = output.toString(StandardCharsets.UTF_8.name());
        assertThat(json).contains("\"visible\":\"visible\"");
        assertThat(json).doesNotContain("hidden");

        TestProvider readerProvider = new TestProvider();
        assertThat(readerProvider.setDefaultReadView(PublicView.class)).isSameAs(readerProvider);
        Object value = readerProvider.readFrom(objectClass(ScopedMessage.class), ScopedMessage.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, new MultivaluedHashMap<>(),
                new ByteArrayInputStream("{\"visible\":\"read\",\"hidden\":\"ignored\"}"
                        .getBytes(StandardCharsets.UTF_8)));

        assertThat(value).isInstanceOf(ScopedMessage.class);
        ScopedMessage message = (ScopedMessage) value;
        assertThat(message.visible).isEqualTo("read");
        assertThat(message.hidden).isNull();
    }

    @Test
    public void providerUsesReaderAndWriterModifiersOncePerCall() throws IOException {
        TestProvider provider = new TestProvider();
        AtomicBoolean readerModifierCalled = new AtomicBoolean(false);
        AtomicBoolean writerModifierCalled = new AtomicBoolean(false);

        ObjectReaderInjector.set(new ObjectReaderModifier() {
            @Override
            public ObjectReader modify(EndpointConfigBase<?> endpoint,
                                       MultivaluedMap<String, String> httpHeaders,
                                       JavaType resultType,
                                       ObjectReader reader,
                                       JsonParser parser) {
                readerModifierCalled.set(true);
                assertThat(endpoint.getReader()).isNotNull();
                assertThat(reader).isNotNull();
                assertThat(resultType.getRawClass()).isEqualTo(Message.class);
                assertThat(httpHeaders.getFirst("request-id")).isEqualTo("read-1");
                return reader;
            }
        });
        MultivaluedMap<String, String> requestHeaders = new MultivaluedHashMap<>();
        requestHeaders.add("request-id", "read-1");
        Object readValue = provider.readFrom(objectClass(Message.class), Message.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, requestHeaders,
                new ByteArrayInputStream("{\"text\":\"modified\",\"count\":7}".getBytes(StandardCharsets.UTF_8)));

        assertThat(readerModifierCalled).isTrue();
        assertThat(readValue).isInstanceOf(Message.class);
        assertThat(ObjectReaderInjector.get()).isNull();

        ObjectWriterInjector.set(new ObjectWriterModifier() {
            @Override
            public ObjectWriter modify(EndpointConfigBase<?> endpoint,
                                       MultivaluedMap<String, Object> httpHeaders,
                                       Object valueToWrite,
                                       ObjectWriter writer,
                                       JsonGenerator generator) {
                writerModifierCalled.set(true);
                assertThat(endpoint.getWriter()).isNotNull();
                assertThat(writer).isNotNull();
                assertThat(valueToWrite).isInstanceOf(Message.class);
                assertThat(httpHeaders.getFirst("request-id")).isEqualTo("write-1");
                return writer;
            }
        });
        MultivaluedMap<String, Object> responseHeaders = new MultivaluedHashMap<>();
        responseHeaders.add("request-id", "write-1");
        provider.writeTo(new Message("out", 11), Message.class, Message.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, responseHeaders, new ByteArrayOutputStream());

        assertThat(writerModifierCalled).isTrue();
        assertThat(ObjectWriterInjector.get()).isNull();
    }

    @Test
    public void providerHonorsFeatureTogglesUntouchablesAndMapperLookupOrder() {
        TestProvider provider = new TestProvider();

        assertThat(provider.isEnabled(JaxRSFeature.ALLOW_EMPTY_INPUT)).isTrue();
        assertThat(provider.isEnabled(JaxRSFeature.CACHE_ENDPOINT_READERS)).isTrue();
        assertThat(provider.isEnabled(JaxRSFeature.CACHE_ENDPOINT_WRITERS)).isTrue();
        assertThat(provider.isEnabled(JaxRSFeature.ADD_NO_SNIFF_HEADER)).isFalse();

        assertThat(provider.enable(JaxRSFeature.ADD_NO_SNIFF_HEADER)).isSameAs(provider);
        assertThat(provider.isEnabled(JaxRSFeature.ADD_NO_SNIFF_HEADER)).isTrue();
        provider.disable(JaxRSFeature.ALLOW_EMPTY_INPUT, JaxRSFeature.CACHE_ENDPOINT_READERS);
        assertThat(provider.isEnabled(JaxRSFeature.ALLOW_EMPTY_INPUT)).isFalse();
        assertThat(provider.isEnabled(JaxRSFeature.CACHE_ENDPOINT_READERS)).isFalse();
        provider.configure(JaxRSFeature.ALLOW_EMPTY_INPUT, true);
        assertThat(provider.isEnabled(JaxRSFeature.ALLOW_EMPTY_INPUT)).isTrue();

        assertThat(provider.isReadable(String.class, String.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE)).isFalse();
        provider.removeUntouchable(String.class);
        assertThat(provider.isReadable(String.class, String.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE)).isTrue();
        provider.addUntouchable(String.class);
        assertThat(provider.isReadable(String.class, String.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE)).isFalse();

        ObjectMapper configuredMapper = new ObjectMapper();
        ObjectMapper providerMapper = new ObjectMapper();
        provider.setMapper(configuredMapper);
        provider.locatedMapper = providerMapper;
        assertThat(provider.locateMapper(Message.class, MediaType.APPLICATION_JSON_TYPE)).isSameAs(configuredMapper);
        provider.setMapper(null);
        assertThat(provider.locateMapper(Message.class, MediaType.APPLICATION_JSON_TYPE)).isSameAs(providerMapper);
        provider.enable(JaxRSFeature.DYNAMIC_OBJECT_MAPPER_LOOKUP);
        assertThat(provider.locateMapper(Message.class, MediaType.APPLICATION_JSON_TYPE)).isSameAs(providerMapper);
    }

    @Test
    public void providerReturnsNullForEmptyInputWhenEmptyInputIsAllowed() throws IOException {
        TestProvider provider = new TestProvider();

        Object value = provider.readFrom(objectClass(Message.class), Message.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, new MultivaluedHashMap<>(), new ByteArrayInputStream(new byte[0]));

        assertThat(value).isNull();
    }

    @Test
    public void mapperConfiguratorCachesAndConfiguresMappers() {
        TestMapperConfigurator configurator = new TestMapperConfigurator(null, new Annotations[] {Annotations.JACKSON});

        ObjectMapper defaultMapper = configurator.getDefaultMapper();
        assertThat(configurator.getDefaultMapper()).isSameAs(defaultMapper);
        assertThat(configurator.getConfiguredMapper()).isNull();

        configurator.configure(SerializationFeature.INDENT_OUTPUT, true);
        configurator.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        configurator.configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true);
        configurator.configure(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION, true);
        assertThat(defaultMapper.isEnabled(SerializationFeature.INDENT_OUTPUT)).isTrue();
        assertThat(defaultMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(defaultMapper.getFactory().isEnabled(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)).isTrue();
        assertThat(defaultMapper.getFactory().isEnabled(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION)).isTrue();

        ObjectMapper explicitMapper = new ObjectMapper();
        configurator.setMapper(explicitMapper);
        assertThat(configurator.getConfiguredMapper()).isSameAs(explicitMapper);
        configurator.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
        assertThat(explicitMapper.isEnabled(SerializationFeature.WRAP_ROOT_VALUE)).isTrue();
    }

    @Test
    public void endpointConfigAppliesAnnotationsToReadersAndWriters() {
        ObjectMapper mapper = new ObjectMapper();
        TestEndpointConfig readerConfig = new TestEndpointConfig(mapper.getDeserializationConfig())
                .addForReading(new Annotation[] {jsonViewAnnotation(PublicView.class), jacksonFeaturesAnnotation()})
                .initForReading(mapper.reader());
        TestEndpointConfig writerConfig = new TestEndpointConfig(mapper.getSerializationConfig())
                .addForWriting(new Annotation[] {jsonRootNameAnnotation("root"), jacksonFeaturesAnnotation()})
                .initForWriting(mapper.writer());

        assertThat(readerConfig.getActiveView()).isEqualTo(PublicView.class);
        assertThat(readerConfig.getReader().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(readerConfig.getReader().isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)).isTrue();
        assertThat(writerConfig.getRootName()).isEqualTo("root");
        assertThat(writerConfig.getWriter().isEnabled(SerializationFeature.INDENT_OUTPUT)).isTrue();
        assertThat(writerConfig.getWriter().isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
        assertThatThrownBy(new TestEndpointConfig(mapper.getDeserializationConfig())::getReader)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(new TestEndpointConfig(mapper.getSerializationConfig())::getWriter)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void keysMapsAndEndpointBeanPropertyExposeStablePublicBehavior() {
        JacksonFeatures features = jacksonFeaturesAnnotation();
        AnnotationBundleKey key = new AnnotationBundleKey(new Annotation[] {features}, Message.class);
        AnnotationBundleKey equalKey = new AnnotationBundleKey(new Annotation[] {features}, Message.class);
        AnnotationBundleKey differentKey = new AnnotationBundleKey(NO_ANNOTATIONS, Message.class);

        assertThat(key).isEqualTo(equalKey);
        assertThat(key.hashCode()).isEqualTo(equalKey.hashCode());
        assertThat(key).isNotEqualTo(differentKey);
        assertThat(key.immutableKey()).isEqualTo(key);
        assertThat(key.toString()).contains(Message.class.getName());

        ClassKey stringKey = new ClassKey(String.class);
        ClassKey integerKey = new ClassKey(Integer.class);
        assertThat(stringKey).isEqualTo(new ClassKey(String.class));
        assertThat(stringKey.compareTo(integerKey)).isNotZero();
        stringKey.reset(Integer.class);
        assertThat(stringKey).isEqualTo(integerKey);
        assertThat(stringKey.toString()).isEqualTo(Integer.class.getName());

        LRUMap<String, Integer> lruMap = new LRUMap<>(2, 2);
        lruMap.put("a", 1);
        lruMap.put("b", 2);
        lruMap.get("a");
        lruMap.put("c", 3);
        assertThat(lruMap).containsEntry("a", 1).containsEntry("c", 3).doesNotContainKey("b");

        EndpointAsBeanProperty property = new EndpointAsBeanProperty(EndpointAsBeanProperty.ENDPOINT_NAME,
                new ObjectMapper().constructType(Message.class), new Annotation[] {features});
        BeanProperty.Std typedProperty = property.withType(new ObjectMapper().constructType(String.class));
        assertThat(((java.util.function.Function<Class<JacksonFeatures>, JacksonFeatures>) property::getAnnotation).apply(JacksonFeatures.class)).isSameAs(features);
        assertThat(typedProperty.getType().getRawClass()).isEqualTo(String.class);
    }

    @Test
    public void noContentSuppliersCreateIoExceptionsWithDocumentedMessage() {
        assertThat(new JaxRS1NoContentExceptionSupplier().createNoContentException())
                .isInstanceOf(IOException.class)
                .hasMessage(NoContentExceptionSupplier.NO_CONTENT_MESSAGE);
        assertThat(new JaxRS2NoContentExceptionSupplier().createNoContentException())
                .isInstanceOf(IOException.class)
                .hasMessage(NoContentExceptionSupplier.NO_CONTENT_MESSAGE);
    }

    @Test
    public void exceptionMappersReturnPlainTextBadRequestResponsesForJacksonFailures() {
        RuntimeDelegate.setInstance(new TestRuntimeDelegate());
        ObjectMapper mapper = new ObjectMapper();

        JsonParseException parseException = assertThrows(JsonParseException.class, () -> {
            try (JsonParser parser = mapper.getFactory().createParser("{\"text\":\"unterminated")) {
                while (parser.nextToken() != null) {
                    // Consume tokens until the parser reports the malformed JSON input.
                }
            }
        });
        Response parseResponse = new JsonParseExceptionMapper().toResponse(parseException);
        assertBadRequestPlainTextResponse(parseResponse, parseException);

        JsonMappingException mappingException = assertThrows(JsonMappingException.class,
                () -> mapper.readValue("{\"text\":\"ok\",\"count\":\"not-a-number\"}", Message.class));
        Response mappingResponse = new JsonMappingExceptionMapper().toResponse(mappingException);
        assertBadRequestPlainTextResponse(mappingResponse, mappingException);
    }

    @Test
    public void jaxRsFeatureMasksCollectAllDefaults() {
        int defaults = JaxRSFeature.collectDefaults();

        for (JaxRSFeature feature : JaxRSFeature.values()) {
            assertThat(feature.enabledIn(defaults)).isEqualTo(feature.enabledByDefault());
            assertThat(feature.getMask()).isEqualTo(1 << feature.ordinal());
        }
        assertThat(JaxRSFeature.valueOf("ALLOW_EMPTY_INPUT")).isSameAs(JaxRSFeature.ALLOW_EMPTY_INPUT);
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> objectClass(Class<?> rawClass) {
        return (Class<Object>) rawClass;
    }

    private static void assertBadRequestPlainTextResponse(Response response, Exception exception) {
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
        assertThat(response.getMediaType()).isEqualTo(MediaType.TEXT_PLAIN_TYPE);
        assertThat(response.hasEntity()).isTrue();
        assertThat(response.getEntity()).isEqualTo(exception.getMessage());
    }

    private static JacksonFeatures jacksonFeaturesAnnotation() {
        return new JacksonFeatures() {
            @Override
            public DeserializationFeature[] deserializationEnable() {
                return new DeserializationFeature[] {DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY};
            }

            @Override
            public DeserializationFeature[] deserializationDisable() {
                return new DeserializationFeature[] {DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES};
            }

            @Override
            public SerializationFeature[] serializationEnable() {
                return new SerializationFeature[] {SerializationFeature.INDENT_OUTPUT};
            }

            @Override
            public SerializationFeature[] serializationDisable() {
                return new SerializationFeature[] {SerializationFeature.WRITE_DATES_AS_TIMESTAMPS};
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return JacksonFeatures.class;
            }
        };
    }

    private static JsonView jsonViewAnnotation(Class<?> view) {
        return new JsonView() {
            @Override
            public Class<?>[] value() {
                return new Class<?>[] {view};
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return JsonView.class;
            }
        };
    }

    private static JsonRootName jsonRootNameAnnotation(String rootName) {
        return new JsonRootName() {
            @Override
            public String value() {
                return rootName;
            }

            @Override
            public String namespace() {
                return "";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return JsonRootName.class;
            }
        };
    }

    public static class Message {
        public String text;
        public int count;

        public Message() {
        }

        Message(String text, int count) {
            this.text = text;
            this.count = count;
        }
    }

    public static class PublicView {
    }

    public static class InternalView extends PublicView {
    }

    public static class ScopedMessage {
        @JsonView(PublicView.class)
        public String visible;

        @JsonView(InternalView.class)
        public String hidden;

        public ScopedMessage() {
        }

        ScopedMessage(String visible, String hidden) {
            this.visible = visible;
            this.hidden = hidden;
        }
    }

    private static final class TestRuntimeDelegate extends RuntimeDelegate {
        @Override
        public UriBuilder createUriBuilder() {
            throw new UnsupportedOperationException("URI building is not used by these tests");
        }

        @Override
        public Response.ResponseBuilder createResponseBuilder() {
            return new TestResponseBuilder();
        }

        @Override
        public Variant.VariantListBuilder createVariantListBuilder() {
            throw new UnsupportedOperationException("Variant building is not used by these tests");
        }

        @Override
        public <T> T createEndpoint(Application application, Class<T> endpointType) {
            throw new UnsupportedOperationException("Endpoint creation is not used by these tests");
        }

        @Override
        public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
            throw new UnsupportedOperationException("Header delegates are not used by these tests");
        }

        @Override
        public Link.Builder createLinkBuilder() {
            throw new UnsupportedOperationException("Link building is not used by these tests");
        }
    }

    private static final class TestResponseBuilder extends Response.ResponseBuilder {
        private int status = Response.Status.OK.getStatusCode();
        private Response.StatusType statusInfo = Response.Status.OK;
        private Object entity;
        private MediaType mediaType;
        private final MultivaluedMap<String, Object> metadata = new MultivaluedHashMap<>();

        @Override
        public Response build() {
            return new TestResponse(status, statusInfo, entity, mediaType, metadata);
        }

        @Override
        public Response.ResponseBuilder clone() {
            TestResponseBuilder builder = new TestResponseBuilder();
            builder.status = status;
            builder.statusInfo = statusInfo;
            builder.entity = entity;
            builder.mediaType = mediaType;
            builder.metadata.putAll(metadata);
            return builder;
        }

        @Override
        public Response.ResponseBuilder status(int status) {
            return status(status, defaultReasonPhrase(status));
        }

        @Override
        public Response.ResponseBuilder status(int status, String reasonPhrase) {
            this.status = status;
            this.statusInfo = new TestStatusType(status, reasonPhrase);
            return this;
        }

        @Override
        public Response.ResponseBuilder entity(Object entity) {
            this.entity = entity;
            return this;
        }

        @Override
        public Response.ResponseBuilder entity(Object entity, Annotation[] annotations) {
            return entity(entity);
        }

        @Override
        public Response.ResponseBuilder allow(String... methods) {
            throw new UnsupportedOperationException("Allowed methods are not used by these tests");
        }

        @Override
        public Response.ResponseBuilder allow(Set<String> methods) {
            throw new UnsupportedOperationException("Allowed methods are not used by these tests");
        }

        @Override
        public Response.ResponseBuilder cacheControl(CacheControl cacheControl) {
            throw new UnsupportedOperationException("Cache control is not used by these tests");
        }

        @Override
        public Response.ResponseBuilder encoding(String encoding) {
            throw new UnsupportedOperationException("Encoding is not used by these tests");
        }

        @Override
        public Response.ResponseBuilder header(String name, Object value) {
            metadata.add(name, value);
            return this;
        }

        @Override
        public Response.ResponseBuilder replaceAll(MultivaluedMap<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public Response.ResponseBuilder language(String language) {
            throw new UnsupportedOperationException("Language is not used by these tests");
        }

        @Override
        public Response.ResponseBuilder language(Locale language) {
            throw new UnsupportedOperationException("Language is not used by these tests");
        }

        @Override
        public Response.ResponseBuilder type(MediaType mediaType) {
            this.mediaType = mediaType;
            metadata.putSingle("Content-Type", mediaType);
            return this;
        }

        @Override
        public Response.ResponseBuilder type(String type) {
            if (MediaType.TEXT_PLAIN.equals(type)) {
                return type(MediaType.TEXT_PLAIN_TYPE);
            }
            throw new UnsupportedOperationException("Only text/plain response media type is used by these tests");
        }

        @Override
        public Response.ResponseBuilder variant(Variant variant) {
            throw new UnsupportedOperationException("Variants are not used by these tests");
        }

        @Override
        public Response.ResponseBuilder contentLocation(URI location) {
            throw new UnsupportedOperationException("Content location is not used by these tests");
        }

        @Override
        public Response.ResponseBuilder cookie(NewCookie... cookies) {
            throw new UnsupportedOperationException("Cookies are not used by these tests");
        }

        @Override
        public Response.ResponseBuilder expires(Date expires) {
            throw new UnsupportedOperationException("Expiration is not used by these tests");
        }

        @Override
        public Response.ResponseBuilder lastModified(Date lastModified) {
            throw new UnsupportedOperationException("Last modified is not used by these tests");
        }

        @Override
        public Response.ResponseBuilder location(URI location) {
            throw new UnsupportedOperationException("Location is not used by these tests");
        }

        @Override
        public Response.ResponseBuilder tag(EntityTag tag) {
            throw new UnsupportedOperationException("Entity tags are not used by these tests");
        }

        @Override
        public Response.ResponseBuilder tag(String tag) {
            throw new UnsupportedOperationException("Entity tags are not used by these tests");
        }

        @Override
        public Response.ResponseBuilder variants(Variant... variants) {
            throw new UnsupportedOperationException("Variants are not used by these tests");
        }

        @Override
        public Response.ResponseBuilder variants(List<Variant> variants) {
            throw new UnsupportedOperationException("Variants are not used by these tests");
        }

        @Override
        public Response.ResponseBuilder links(Link... links) {
            throw new UnsupportedOperationException("Links are not used by these tests");
        }

        @Override
        public Response.ResponseBuilder link(URI uri, String rel) {
            throw new UnsupportedOperationException("Links are not used by these tests");
        }

        @Override
        public Response.ResponseBuilder link(String uri, String rel) {
            throw new UnsupportedOperationException("Links are not used by these tests");
        }

        private static String defaultReasonPhrase(int status) {
            Response.Status matchingStatus = Response.Status.fromStatusCode(status);
            if (matchingStatus == null) {
                return "";
            }
            return matchingStatus.getReasonPhrase();
        }
    }

    private static final class TestResponse extends Response {
        private final int status;
        private final Response.StatusType statusInfo;
        private final Object entity;
        private final MediaType mediaType;
        private final MultivaluedMap<String, Object> metadata;

        private TestResponse(int status, Response.StatusType statusInfo, Object entity, MediaType mediaType,
                             MultivaluedMap<String, Object> metadata) {
            this.status = status;
            this.statusInfo = statusInfo;
            this.entity = entity;
            this.mediaType = mediaType;
            this.metadata = new MultivaluedHashMap<>(metadata);
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public Response.StatusType getStatusInfo() {
            return statusInfo;
        }

        @Override
        public Object getEntity() {
            return entity;
        }

        @Override
        public <T> T readEntity(Class<T> entityType) {
            return entityType.cast(entity);
        }

        @Override
        public <T> T readEntity(GenericType<T> entityType) {
            throw new UnsupportedOperationException("Generic entity reads are not used by these tests");
        }

        @Override
        public <T> T readEntity(Class<T> entityType, Annotation[] annotations) {
            return readEntity(entityType);
        }

        @Override
        public <T> T readEntity(GenericType<T> entityType, Annotation[] annotations) {
            throw new UnsupportedOperationException("Generic entity reads are not used by these tests");
        }

        @Override
        public boolean hasEntity() {
            return entity != null;
        }

        @Override
        public boolean bufferEntity() {
            return true;
        }

        @Override
        public void close() {
            // This in-memory response does not hold resources.
        }

        @Override
        public MediaType getMediaType() {
            return mediaType;
        }

        @Override
        public Locale getLanguage() {
            return null;
        }

        @Override
        public int getLength() {
            return -1;
        }

        @Override
        public Set<String> getAllowedMethods() {
            return Collections.emptySet();
        }

        @Override
        public Map<String, NewCookie> getCookies() {
            return Collections.emptyMap();
        }

        @Override
        public EntityTag getEntityTag() {
            return null;
        }

        @Override
        public Date getDate() {
            return null;
        }

        @Override
        public Date getLastModified() {
            return null;
        }

        @Override
        public URI getLocation() {
            return null;
        }

        @Override
        public Set<Link> getLinks() {
            return Collections.emptySet();
        }

        @Override
        public boolean hasLink(String relation) {
            return false;
        }

        @Override
        public Link getLink(String relation) {
            return null;
        }

        @Override
        public Link.Builder getLinkBuilder(String relation) {
            return null;
        }

        @Override
        public MultivaluedMap<String, Object> getMetadata() {
            return metadata;
        }

        @Override
        public MultivaluedMap<String, String> getStringHeaders() {
            MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
            for (Map.Entry<String, List<Object>> entry : metadata.entrySet()) {
                for (Object value : entry.getValue()) {
                    headers.add(entry.getKey(), String.valueOf(value));
                }
            }
            return headers;
        }

        @Override
        public String getHeaderString(String name) {
            return getStringHeaders().getFirst(name);
        }
    }

    private static final class TestStatusType implements Response.StatusType {
        private final int statusCode;
        private final String reasonPhrase;

        private TestStatusType(int statusCode, String reasonPhrase) {
            this.statusCode = statusCode;
            this.reasonPhrase = reasonPhrase;
        }

        @Override
        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public Response.Status.Family getFamily() {
            return Response.Status.Family.familyOf(statusCode);
        }

        @Override
        public String getReasonPhrase() {
            return reasonPhrase;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Response.StatusType)) {
                return false;
            }
            Response.StatusType that = (Response.StatusType) other;
            return statusCode == that.getStatusCode() && getFamily() == that.getFamily()
                    && reasonPhrase.equals(that.getReasonPhrase());
        }

        @Override
        public int hashCode() {
            int result = statusCode;
            result = 31 * result + getFamily().hashCode();
            result = 31 * result + reasonPhrase.hashCode();
            return result;
        }
    }

    private static final class TestMapperConfigurator
            extends MapperConfiguratorBase<TestMapperConfigurator, ObjectMapper> {
        private TestMapperConfigurator(ObjectMapper mapper, Annotations[] annotationsToUse) {
            super(mapper, annotationsToUse);
        }

        @Override
        public ObjectMapper getConfiguredMapper() {
            return _mapper;
        }

        @Override
        public ObjectMapper getDefaultMapper() {
            if (_defaultMapper == null) {
                _defaultMapper = new ObjectMapper();
                _setAnnotations(_defaultMapper, _defaultAnnotationsToUse);
            }
            return _defaultMapper;
        }

        @Override
        protected ObjectMapper mapper() {
            if (_mapper != null) {
                return _mapper;
            }
            if (_defaultMapper == null) {
                _defaultMapper = new ObjectMapper();
                _setAnnotations(_defaultMapper, _defaultAnnotationsToUse);
            }
            return _defaultMapper;
        }

        @Override
        protected AnnotationIntrospector _resolveIntrospectors(Annotations[] annotationsToUse) {
            return new JacksonAnnotationIntrospector();
        }
    }

    private static final class TestEndpointConfig extends EndpointConfigBase<TestEndpointConfig> {
        private TestEndpointConfig(MapperConfig<?> config) {
            super(config);
        }

        private TestEndpointConfig addForReading(Annotation[] annotations) {
            return add(annotations, false);
        }

        private TestEndpointConfig addForWriting(Annotation[] annotations) {
            return add(annotations, true);
        }

        private TestEndpointConfig initForReading(ObjectReader reader) {
            return initReader(reader);
        }

        private TestEndpointConfig initForWriting(ObjectWriter writer) {
            return initWriter(writer);
        }

        @Override
        public Object modifyBeforeWrite(Object value) {
            return value;
        }
    }

    private static final class TestProvider extends ProviderBase<TestProvider, ObjectMapper, TestEndpointConfig,
            TestMapperConfigurator> {
        private ObjectMapper locatedMapper;

        private TestProvider() {
            super(new TestMapperConfigurator(null, new Annotations[] {Annotations.JACKSON}));
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        @Override
        protected boolean hasMatchingMediaType(MediaType mediaType) {
            if (mediaType == null || mediaType.isWildcardType() || mediaType.isWildcardSubtype()) {
                return true;
            }
            String subtype = mediaType.getSubtype();
            return "json".equalsIgnoreCase(subtype) || subtype.toLowerCase(Locale.ROOT).endsWith("+json");
        }

        @Override
        protected ObjectMapper _locateMapperViaProvider(Class<?> type, MediaType mediaType) {
            return locatedMapper;
        }

        @Override
        protected TestEndpointConfig _configForReading(ObjectReader reader, Annotation[] annotations) {
            return new TestEndpointConfig(reader.getConfig()).addForReading(annotations).initForReading(reader);
        }

        @Override
        protected TestEndpointConfig _configForWriting(ObjectWriter writer, Annotation[] annotations) {
            return new TestEndpointConfig(writer.getConfig()).addForWriting(annotations).initForWriting(writer);
        }
    }
}
