/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_media.jersey_media_json_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.bind.annotation.XmlElement;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.junit.jupiter.api.Test;

public class Jersey_media_json_jacksonTest {
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];

    @Test
    void jaxbElementNameIsUsedWhenWritingJson() throws IOException {
        JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
        JaxbProfile profile = new JaxbProfile("Ada Lovelace", 5);

        String json = writeProfile(provider, profile);

        assertThat(json).contains("\"display_name\":\"Ada Lovelace\"");
        assertThat(json).contains("\"rating\":5");
        assertThat(json).doesNotContain("\"name\":");
    }

    @Test
    void jaxbElementNameIsUsedWhenReadingJson() throws IOException {
        JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
        String json = """
                {"display_name":"Grace Hopper","rating":4}
                """;

        JaxbProfile profile = readProfile(provider, json);

        assertThat(profile.name).isEqualTo("Grace Hopper");
        assertThat(profile.rating).isEqualTo(4);
    }

    @Test
    void customObjectMapperConfigurationIsUsedForJsonBinding() throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.WRAP_ROOT_VALUE)
                .enable(DeserializationFeature.UNWRAP_ROOT_VALUE);
        JacksonJsonProvider provider = new JacksonJsonProvider();
        provider.setMapper(mapper);
        RootWrappedPayload payload = new RootWrappedPayload("accepted", 7);

        String json = writeRootWrappedPayload(provider, payload);
        RootWrappedPayload restoredPayload = readRootWrappedPayload(provider, json);

        assertThat(json).startsWith("{\"payload\":{");
        assertThat(json).contains("\"state\":\"accepted\"", "\"priority\":7");
        assertThat(restoredPayload.state).isEqualTo("accepted");
        assertThat(restoredPayload.priority).isEqualTo(7);
    }

    private static String writeProfile(JacksonJaxbJsonProvider provider, JaxbProfile profile) throws IOException {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        provider.writeTo(profile, JaxbProfile.class, JaxbProfile.class, NO_ANNOTATIONS, MediaType.APPLICATION_JSON_TYPE,
                headers, output);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static JaxbProfile readProfile(JacksonJaxbJsonProvider provider, String json) throws IOException {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        return (JaxbProfile) provider.readFrom(Object.class, JaxbProfile.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, headers, input);
    }

    private static String writeRootWrappedPayload(JacksonJsonProvider provider, RootWrappedPayload payload)
            throws IOException {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        provider.writeTo(payload, RootWrappedPayload.class, RootWrappedPayload.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, headers, output);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static RootWrappedPayload readRootWrappedPayload(JacksonJsonProvider provider, String json)
            throws IOException {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        return (RootWrappedPayload) provider.readFrom(Object.class, RootWrappedPayload.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, headers, input);
    }

    public static final class JaxbProfile {
        @XmlElement(name = "display_name")
        public String name;
        public int rating;

        public JaxbProfile() {
        }

        JaxbProfile(String name, int rating) {
            this.name = name;
            this.rating = rating;
        }
    }

    @JsonRootName("payload")
    public static final class RootWrappedPayload {
        public String state;
        public int priority;

        public RootWrappedPayload() {
        }

        RootWrappedPayload(String state, int priority) {
            this.state = state;
            this.priority = priority;
        }
    }
}
