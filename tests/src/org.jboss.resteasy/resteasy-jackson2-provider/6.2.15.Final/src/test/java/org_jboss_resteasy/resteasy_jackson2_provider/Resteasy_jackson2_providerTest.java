/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_jackson2_provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jakarta.rs.cfg.EndpointConfigBase;
import com.fasterxml.jackson.jakarta.rs.cfg.ObjectWriterModifier;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.annotations.providers.jackson.Formatted;
import org.jboss.resteasy.plugins.providers.jackson.Jackson2JsonpInterceptor;
import org.jboss.resteasy.plugins.providers.jackson.JsonProcessingExceptionMapper;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyObjectWriterInjector;
import org.jboss.resteasy.plugins.providers.jackson.WhiteListPolymorphicTypeValidatorBuilder;
import org.jboss.resteasy.spi.AsyncOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Resteasy_jackson2_providerTest {
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
    private static final MediaType JSON = MediaType.APPLICATION_JSON_TYPE;

    @Test
    void providerReportsJsonEntitySupport() {
        ResteasyJackson2Provider provider = new ResteasyJackson2Provider();

        assertThat(provider.isReadable(Pet.class, Pet.class, NO_ANNOTATIONS, JSON)).isTrue();
        assertThat(provider.isWriteable(Pet.class, Pet.class, NO_ANNOTATIONS, JSON)).isTrue();
        assertThat(provider.isReadable(Pet.class, Pet.class, NO_ANNOTATIONS, new MediaType("application", "hal+json")))
                .isTrue();
        assertThat(provider.isWriteable(Pet.class, Pet.class, NO_ANNOTATIONS, new MediaType("text", "json")))
                .isTrue();
    }

    @Test
    void providerRoundTripsPojoAndJavaTimeValue() throws IOException {
        ResteasyJackson2Provider provider = new ResteasyJackson2Provider();
        Pet input = new Pet("Ada", 3, LocalDate.of(2024, 2, 29), Arrays.asList("friendly", "trained"));

        String json = writeEntity(provider, input, Pet.class, Pet.class, NO_ANNOTATIONS);
        Pet output = readEntity(provider, Pet.class, Pet.class, json);

        assertThat(json).contains("\"name\":\"Ada\"");
        assertThat(json).contains("\"adoptedOn\":[2024,2,29]");
        assertThat(output.name).isEqualTo(input.name);
        assertThat(output.age).isEqualTo(input.age);
        assertThat(output.adoptedOn).isEqualTo(input.adoptedOn);
        assertThat(output.tags).containsExactlyElementsOf(input.tags);
    }

    @Test
    void providerUsesGenericTypeInformationForCollections() throws IOException {
        ResteasyJackson2Provider provider = new ResteasyJackson2Provider();
        Type petListType = new PetListType().getType();
        List<Pet> pets = Arrays.asList(
                new Pet("Ada", 3, LocalDate.of(2024, 2, 29), List.of("trained")),
                new Pet("Grace", 5, LocalDate.of(2023, 1, 2), List.of("quiet")));

        String json = writeEntity(provider, pets, ArrayList.class, petListType, NO_ANNOTATIONS);
        List<Pet> output = readEntity(provider, List.class, petListType, json);

        assertThat(json).startsWith("[");
        assertThat(output).hasSize(2);
        assertThat(output.get(0)).isInstanceOf(Pet.class);
        assertThat(output.get(0).name).isEqualTo("Ada");
        assertThat(output.get(1).adoptedOn).isEqualTo(LocalDate.of(2023, 1, 2));
    }

    @Test
    void formattedAnnotationEnablesPrettyPrintedOutput() throws IOException {
        ResteasyJackson2Provider provider = new ResteasyJackson2Provider();
        Pet pet = new Pet("Ada", 3, LocalDate.of(2024, 2, 29), List.of("friendly"));

        String json = writeEntity(provider, pet, Pet.class, Pet.class, new Annotation[] { new FormattedAnnotation() });

        assertThat(json).contains("\n");
        assertThat(json).contains("  \"name\" : \"Ada\"");
    }

    @Test
    void asyncWriteToSerializesEntityToAsyncOutputStream() throws Exception {
        ResteasyJackson2Provider provider = new ResteasyJackson2Provider();
        BufferingAsyncOutputStream output = new BufferingAsyncOutputStream();
        Pet pet = new Pet("Turing", 4, LocalDate.of(2022, 12, 1), List.of("curious"));

        provider.asyncWriteTo(pet, Pet.class, Pet.class, NO_ANNOTATIONS, JSON, new MultivaluedHashMap<>(), output)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertThat(output.content()).contains("\"name\":\"Turing\"");
        assertThat(output.content()).contains("\"adoptedOn\":[2022,12,1]");
    }

    @Test
    void objectWriterInjectorCustomizesProviderSerializationForThreadContextClassLoader() throws IOException {
        ResteasyJackson2Provider provider = new ResteasyJackson2Provider();
        Map<String, Integer> entity = new LinkedHashMap<>();
        entity.put("z", 26);
        entity.put("a", 1);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader scopedClassLoader = new ClassLoader(previous) { };
        ResteasyObjectWriterInjector.set(scopedClassLoader, new SortingObjectWriterModifier());

        Thread.currentThread().setContextClassLoader(scopedClassLoader);
        try {
            String json = writeEntity(provider, entity, LinkedHashMap.class, LinkedHashMap.class, NO_ANNOTATIONS);

            assertThat(json).isEqualTo("{\"a\":1,\"z\":26}");
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void emptyJsonInputProducesNullEntity() throws IOException {
        ResteasyJackson2Provider provider = new ResteasyJackson2Provider();

        Pet output = readEntity(provider, Pet.class, Pet.class, "");

        assertThat(output).isNull();
    }

    @Test
    void jsonProcessingExceptionMapperBuildsBadRequestResponse() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonProcessingException exception = assertThrows(JsonProcessingException.class, () -> mapper.readTree("{"));
        JsonProcessingExceptionMapper exceptionMapper = new JsonProcessingExceptionMapper();

        try (Response response = exceptionMapper.toResponse(exception)) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
            assertThat(response.getEntity()).isInstanceOf(String.class);
            assertThat((String) response.getEntity()).isNotBlank();
        }
    }

    @Test
    void jsonpInterceptorExposesCallbackConfigurationAndCompatibleMediaTypes() {
        Jackson2JsonpInterceptor interceptor = new Jackson2JsonpInterceptor();

        assertThat(interceptor.getCallbackQueryParameter())
                .isEqualTo(Jackson2JsonpInterceptor.DEFAULT_CALLBACK_QUERY_PARAMETER);
        assertThat(interceptor.isWrapInTryCatch()).isFalse();

        interceptor.setCallbackQueryParameter("cb");
        interceptor.setWrapInTryCatch(true);

        assertThat(interceptor.getCallbackQueryParameter()).isEqualTo("cb");
        assertThat(interceptor.isWrapInTryCatch()).isTrue();
        assertThat(Jackson2JsonpInterceptor.jsonpCompatibleMediaTypes.getPossible(MediaType.APPLICATION_JSON_TYPE))
                .isNotEmpty();
        assertThat(Jackson2JsonpInterceptor.jsonpCompatibleMediaTypes.getPossible(
                        new MediaType("application", "hal+json")))
                .isNotEmpty();
        assertThat(Jackson2JsonpInterceptor.jsonpCompatibleMediaTypes.getPossible(MediaType.TEXT_PLAIN_TYPE))
                .isEmpty();
    }

    @Test
    void whitelistPolymorphicTypeValidatorBuilderCreatesUsableValidator() {
        assertThat(new WhiteListPolymorphicTypeValidatorBuilder().build()).isNotNull();
    }

    private static String writeEntity(
            ResteasyJackson2Provider provider,
            Object entity,
            Class<?> entityClass,
            Type genericType,
            Annotation[] annotations)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        provider.writeTo(entity, entityClass, genericType, annotations, JSON, new MultivaluedHashMap<>(), output);
        return output.toString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static <T> T readEntity(
            ResteasyJackson2Provider provider, Class<T> entityClass, Type genericType, String json) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        return (T) provider.readFrom(
                (Class<Object>) entityClass, genericType, NO_ANNOTATIONS, JSON, new MultivaluedHashMap<>(), input);
    }

    public static class Pet {
        public String name;
        public int age;
        public LocalDate adoptedOn;
        public List<String> tags;

        public Pet() {
        }

        Pet(String name, int age, LocalDate adoptedOn, List<String> tags) {
            this.name = name;
            this.age = age;
            this.adoptedOn = adoptedOn;
            this.tags = tags;
        }
    }

    private static final class PetListType extends GenericType<List<Pet>> {
    }

    private static final class FormattedAnnotation implements Formatted {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Formatted.class;
        }
    }

    private static final class BufferingAsyncOutputStream extends AsyncOutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();

        @Override
        public CompletionStage<Void> asyncFlush() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> asyncWrite(byte[] bytes, int offset, int length) {
            delegate.write(bytes, offset, length);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void write(int value) {
            delegate.write(value);
        }

        String content() {
            return delegate.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class SortingObjectWriterModifier extends ObjectWriterModifier {
        @Override
        public ObjectWriter modify(
                EndpointConfigBase<?> endpoint,
                MultivaluedMap<String, Object> responseHeaders,
                Object valueToWrite,
                ObjectWriter writer,
                JsonGenerator generator) {
            return writer.with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        }
    }
}
