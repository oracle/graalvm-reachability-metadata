/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_jaxrs.jackson_jaxrs_json_provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.xml.bind.annotation.XmlElement;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.jaxrs.cfg.JaxRSFeature;
import tools.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import tools.jackson.jaxrs.json.JacksonJsonProvider;
import tools.jackson.jaxrs.json.JsonEndpointConfig;
import tools.jackson.jaxrs.json.JsonMapperConfigurator;
import tools.jackson.jaxrs.json.PackageVersion;
import tools.jackson.jaxrs.json.annotation.JSONP;

public class Jackson_jaxrs_json_providerTest {
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
    private static final MediaType VENDOR_JSON_TYPE = new MediaType("application", "vnd.example.resource+json");
    private static final MediaType TEXT_JSON_TYPE = new MediaType("text", "json");
    private static final MediaType TEXT_X_JSON_TYPE = new MediaType("text", "x-json");
    private static final MediaType APPLICATION_JAVASCRIPT_TYPE = new MediaType("application", "javascript");
    private static final MediaType APPLICATION_X_JAVASCRIPT_TYPE = new MediaType("application", "x-javascript");

    @Test
    void providerMatchesJsonVendorJsonAndJavaScriptMediaTypes() {
        JacksonJsonProvider provider = new JacksonJsonProvider();

        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE)).isTrue();
        assertThat(provider.isWriteable(Map.class, Map.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE)).isTrue();
        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS, VENDOR_JSON_TYPE)).isTrue();
        assertThat(provider.isWriteable(Map.class, Map.class, NO_ANNOTATIONS, TEXT_JSON_TYPE)).isTrue();
        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS, TEXT_X_JSON_TYPE)).isTrue();
        assertThat(provider.isWriteable(Map.class, Map.class, NO_ANNOTATIONS, APPLICATION_JAVASCRIPT_TYPE)).isTrue();
        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS, APPLICATION_X_JAVASCRIPT_TYPE)).isTrue();
        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS, MediaType.TEXT_PLAIN_TYPE)).isFalse();
        assertThat(provider.isWriteable(Map.class, Map.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_XML_TYPE)).isFalse();
        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS, null)).isFalse();

        provider.enable(JaxRSFeature.MATCH_ALL_IF_NO_MEDIA_TYPE);
        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS, null)).isTrue();
        assertThat(provider.isWriteable(Map.class, Map.class, NO_ANNOTATIONS, null)).isTrue();
    }

    @Test
    void providerWritesAndReadsStructuredJsonPayloads() throws Exception {
        JacksonJsonProvider provider = new JacksonJsonProvider();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "graalvm");
        payload.put("features", List.of("jax-rs", "json"));
        payload.put("metadata", Map.of("native", true));

        String json = write(provider, payload, Map.class, MediaType.APPLICATION_JSON_TYPE);
        Object readValue = read(provider, json, Map.class, Map.class, MediaType.APPLICATION_JSON_TYPE);

        assertThat(json).isEqualTo(
                "{\"name\":\"graalvm\",\"features\":[\"jax-rs\",\"json\"],\"metadata\":{\"native\":true}}");
        assertThat(readValue).isEqualTo(payload);
    }

    @Test
    void providerUsesGenericTypeForCollectionElements() throws Exception {
        JacksonJsonProvider provider = new JacksonJsonProvider();
        GenericType<List<GenericPayload>> payloadListType = new GenericType<>() {
        };
        List<GenericPayload> payloads = List.of(new GenericPayload("alpha"), new GenericPayload("beta"));

        String json = writeGeneric(provider, payloads, List.class, payloadListType.getType(),
                MediaType.APPLICATION_JSON_TYPE);
        List<?> readValue = read(provider, json, List.class, payloadListType.getType(),
                MediaType.APPLICATION_JSON_TYPE);

        assertThat(json).isEqualTo("[{\"name\":\"alpha\"},{\"name\":\"beta\"}]");
        assertThat(readValue)
                .hasSize(2)
                .allSatisfy(item -> assertThat(item).isInstanceOf(GenericPayload.class))
                .extracting(item -> ((GenericPayload) item).name)
                .containsExactly("alpha", "beta");
    }

    @Test
    void configuredMapperIsUsedForProviderSerialization() throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        JacksonJsonProvider provider = new JacksonJsonProvider(new JsonMapperConfigurator(mapper, null));

        String json = write(provider, Map.of("indented", true), Map.class, MediaType.APPLICATION_JSON_TYPE);

        assertThat(json).contains(System.lineSeparator()).contains("  \"indented\" : true");
        assertThat(provider.version()).isEqualTo(PackageVersion.VERSION);
        assertThat(new PackageVersion().version()).isEqualTo(provider.version());
    }

    @Test
    void defaultJsonpFunctionWrapsWrittenResponses() throws Exception {
        JacksonJsonProvider provider = new JacksonJsonProvider();
        provider.setJSONPFunctionName("callback");

        String json = write(provider, Map.of("answer", 42), Map.class, MediaType.APPLICATION_JSON_TYPE);

        assertThat(json).isEqualTo("callback({\"answer\":42})");
    }

    @Test
    void endpointJsonpAnnotationOverridesDefaultFunctionWithPrefixAndSuffix() throws Exception {
        JacksonJsonProvider provider = new JacksonJsonProvider();
        provider.setJSONPFunctionName("defaultCallback");
        Annotation[] annotations = {new JsonpLiteral("ignored", "prefix(", ")")};

        String json = write(provider, Map.of("wrapped", true), Map.class, MediaType.APPLICATION_JSON_TYPE,
                annotations);

        assertThat(json).isEqualTo("prefix({\"wrapped\":true})");
    }

    @Test
    void endpointConfigAppliesJsonpAnnotationsOnlyForWriting() throws Exception {
        JsonMapper mapper = new JsonMapper();
        Map<String, Object> value = Map.of("endpoint", "json-p");
        Annotation[] annotations = {new JsonpLiteral("endpointCallback", "", "")};

        JsonEndpointConfig writingConfig = JsonEndpointConfig.forWriting(mapper.writer(), annotations, null);
        JsonEndpointConfig readingConfig = JsonEndpointConfig.forReading(mapper.reader(), annotations);

        assertThat(mapper.writeValueAsString(writingConfig.modifyBeforeWrite(value)))
                .isEqualTo("endpointCallback({\"endpoint\":\"json-p\"})");
        assertThat(readingConfig.modifyBeforeWrite(value)).isSameAs(value);
    }

    @Test
    void jsonpDefinitionNormalizesEmptyAnnotationMembers() {
        JSONP.Def methodDefinition = new JSONP.Def("plainFunction");
        JSONP.Def annotationDefinition = new JSONP.Def(new JsonpLiteral("", "before(", ""));

        assertThat(methodDefinition.method).isEqualTo("plainFunction");
        assertThat(methodDefinition.prefix).isNull();
        assertThat(methodDefinition.suffix).isNull();
        assertThat(annotationDefinition.method).isNull();
        assertThat(annotationDefinition.prefix).isEqualTo("before(");
        assertThat(annotationDefinition.suffix).isNull();
    }

    @Test
    void jaxbProviderHonorsJaxbElementNamesWhenReadingAndWriting() throws Exception {
        JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
        JaxbAnnotatedPayload payload = new JaxbAnnotatedPayload("written");

        String json = write(provider, payload, JaxbAnnotatedPayload.class, MediaType.APPLICATION_JSON_TYPE);
        JaxbAnnotatedPayload readValue = read(provider, "{\"jaxbName\":\"read\"}",
                JaxbAnnotatedPayload.class, JaxbAnnotatedPayload.class, MediaType.APPLICATION_JSON_TYPE);

        assertThat(json).isEqualTo("{\"jaxbName\":\"written\"}");
        assertThat(readValue.javaName).isEqualTo("read");
    }

    @Test
    void serviceLoaderPublishesProviderForJaxRsReadersAndWriters() {
        assertThat(ServiceLoader.load(MessageBodyReader.class))
                .anySatisfy(reader -> assertThat(reader).isInstanceOf(JacksonJsonProvider.class));
        assertThat(ServiceLoader.load(MessageBodyWriter.class))
                .anySatisfy(writer -> assertThat(writer).isInstanceOf(JacksonJsonProvider.class));
    }

    private static String write(JacksonJsonProvider provider, Object value, Class<?> rawType, MediaType mediaType,
            Annotation... annotations) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        provider.writeTo(value, rawType, rawType, annotations, mediaType, new MultivaluedHashMap<>(), output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String writeGeneric(JacksonJsonProvider provider, Object value, Class<?> rawType, Type genericType,
            MediaType mediaType) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        provider.writeTo(value, rawType, genericType, NO_ANNOTATIONS, mediaType, new MultivaluedHashMap<>(), output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static <T> T read(JacksonJsonProvider provider, String json, Class<T> rawType, Type genericType,
            MediaType mediaType) throws Exception {
        Object value = provider.readFrom(asObjectClass(rawType), genericType, NO_ANNOTATIONS, mediaType,
                new MultivaluedHashMap<>(), new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        return rawType.cast(value);
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> asObjectClass(Class<?> rawType) {
        return (Class<Object>) rawType;
    }

    private static final class JsonpLiteral implements JSONP {
        private final String value;
        private final String prefix;
        private final String suffix;

        private JsonpLiteral(String value, String prefix, String suffix) {
            this.value = value;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public String prefix() {
            return prefix;
        }

        @Override
        public String suffix() {
            return suffix;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return JSONP.class;
        }
    }

    public static final class GenericPayload {
        public String name;

        public GenericPayload() {
        }

        public GenericPayload(String name) {
            this.name = name;
        }
    }

    public static final class JaxbAnnotatedPayload {
        @XmlElement(name = "jaxbName")
        public String javaName;

        public JaxbAnnotatedPayload() {
        }

        public JaxbAnnotatedPayload(String javaName) {
            this.javaName = javaName;
        }
    }
}
