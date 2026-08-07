/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_jackson2_provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.annotations.providers.jackson.Formatted;
import org.jboss.resteasy.plugins.providers.jackson.JsonProcessingExceptionMapper;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;

public class Resteasy_jackson2_providerTest {
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];

    @Test
    void providerAcceptsStandardAndStructuredJsonMediaTypes() {
        ResteasyJackson2Provider provider = new ResteasyJackson2Provider();
        MediaType vendorJson = MediaType.valueOf("application/vnd.acme.person+json");

        assertThat(provider.isReadable(Person.class, Person.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE))
                .isTrue();
        assertThat(provider.isWriteable(Person.class, Person.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE))
                .isTrue();
        assertThat(provider.isReadable(Person.class, Person.class, NO_ANNOTATIONS, vendorJson)).isTrue();
        assertThat(provider.isWriteable(Person.class, Person.class, NO_ANNOTATIONS, vendorJson)).isTrue();
    }

    @Test
    void writesPojoToJsonAndReadsItBack() throws Exception {
        ResteasyJackson2Provider provider = new ResteasyJackson2Provider();
        Person person = new Person("Ada", 37, new Address("London", "NW1"));
        ByteArrayOutputStream entityStream = new ByteArrayOutputStream();
        MultivaluedMap<String, Object> writeHeaders = new MultivaluedHashMap<>();

        provider.writeTo(
                person,
                Person.class,
                Person.class,
                NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE,
                writeHeaders,
                entityStream);

        String json = entityStream.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"name\":\"Ada\"");
        assertThat(json).contains("\"city\":\"London\"");

        Person roundTripped = (Person) provider.readFrom(
                objectClass(Person.class),
                Person.class,
                NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(roundTripped.name).isEqualTo("Ada");
        assertThat(roundTripped.age).isEqualTo(37);
        assertThat(roundTripped.address.city).isEqualTo("London");
        assertThat(roundTripped.address.postalCode).isEqualTo("NW1");
    }

    @Test
    void readsGenericCollectionsUsingTheSuppliedGenericType() throws Exception {
        ResteasyJackson2Provider provider = new ResteasyJackson2Provider();
        Type peopleType = new GenericType<List<Person>>() {
        }.getType();
        String json = """
                [
                  {"name":"Grace","age":40,"address":{"city":"Arlington","postalCode":"22207"}},
                  {"name":"Katherine","age":45,"address":{"city":"Hampton","postalCode":"23666"}}
                ]
                """;

        @SuppressWarnings("unchecked")
        List<Person> people = (List<Person>) provider.readFrom(
                objectClass(List.class),
                peopleType,
                NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(people).hasSize(2);
        assertThat(people.get(0).name).isEqualTo("Grace");
        assertThat(people.get(0).address.city).isEqualTo("Arlington");
        assertThat(people.get(1).name).isEqualTo("Katherine");
        assertThat(people.get(1).address.postalCode).isEqualTo("23666");
    }

    @Test
    void formattedAnnotationEnablesIndentedJsonOutput() throws Exception {
        ResteasyJackson2Provider provider = new ResteasyJackson2Provider();
        Person person = new Person("Dorothy", 31, new Address("Washington", "20001"));
        ByteArrayOutputStream entityStream = new ByteArrayOutputStream();
        Annotation[] annotations = new Annotation[] {new FormattedLiteral()};

        provider.writeTo(
                person,
                Person.class,
                Person.class,
                annotations,
                MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                entityStream);

        String json = entityStream.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("\n");
        assertThat(json).contains("  \"name\" : \"Dorothy\"");
        assertThat(json).contains("  \"address\" : {");
    }

    @Test
    void jsonProcessingExceptionMapperReturnsBadRequestResponse() {
        JsonProcessingExceptionMapper mapper = new JsonProcessingExceptionMapper();
        JsonParseException exception = new JsonParseException("Unexpected JSON token", JsonLocation.NA);

        try (Response response = mapper.toResponse(exception)) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
            assertThat(response.getEntity()).isInstanceOf(String.class);
            assertThat((String) response.getEntity()).contains("deserialize data");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<Object> objectClass(Class<T> type) {
        return (Class<Object>) type;
    }

    public static class Person {
        public String name;
        public int age;
        public Address address;

        public Person() {
        }

        public Person(String name, int age, Address address) {
            this.name = name;
            this.age = age;
            this.address = address;
        }
    }

    public static class Address {
        public String city;
        public String postalCode;

        public Address() {
        }

        public Address(String city, String postalCode) {
            this.city = city;
            this.postalCode = postalCode;
        }
    }

    private static final class FormattedLiteral implements Formatted {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Formatted.class;
        }
    }
}
