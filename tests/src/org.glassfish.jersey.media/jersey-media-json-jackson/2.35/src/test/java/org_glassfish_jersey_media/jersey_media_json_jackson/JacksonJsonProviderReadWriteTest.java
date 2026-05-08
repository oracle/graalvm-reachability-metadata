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
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.glassfish.jersey.jackson.internal.jackson.jaxrs.base.ProviderBase;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.cfg.JaxRSFeature;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.junit.jupiter.api.Test;

public class JacksonJsonProviderReadWriteTest {
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];

    @Test
    void writesJsonpResponseAndNoSniffHeader() throws Exception {
        JacksonJsonProvider provider = new JacksonJsonProvider();
        provider.setJSONPFunctionName("handlePerson");
        provider.enable(JaxRSFeature.ADD_NO_SNIFF_HEADER);

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", "Ada");
        value.put("active", true);

        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        provider.writeTo(value, value.getClass(), value.getClass(), NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, headers, output);

        assertThat(headers.getFirst(ProviderBase.HEADER_CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        assertThat(output.toString(StandardCharsets.UTF_8.name()))
                .startsWith("handlePerson(")
                .contains("\"name\":\"Ada\"")
                .contains("\"active\":true")
                .endsWith(")");
    }

    @Test
    void readsJsonObjectIntoMapForVendorJsonMediaType() throws Exception {
        JacksonJsonProvider provider = new JacksonJsonProvider();
        String json = """
                {"name":"Grace","count":3,"enabled":true}
                """;

        Object value = provider.readFrom(objectMapClass(), Map.class, NO_ANNOTATIONS,
                MediaType.valueOf("application/vnd.example+json"), new MultivaluedHashMap<>(),
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(value).isInstanceOf(Map.class);
        Map<?, ?> values = (Map<?, ?>) value;
        assertThat(values.get("name")).isEqualTo("Grace");
        assertThat(values.get("count")).isEqualTo(3);
        assertThat(values.get("enabled")).isEqualTo(true);
    }

    @Test
    void recognizesJsonCompatibleMediaTypesAndUntouchableTypes() {
        JacksonJsonProvider provider = new JacksonJsonProvider();

        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS,
                MediaType.valueOf("application/problem+json"))).isTrue();
        assertThat(provider.isWriteable(Map.class, Map.class, NO_ANNOTATIONS,
                MediaType.valueOf(JacksonJsonProvider.MIME_JAVASCRIPT))).isTrue();
        assertThat(provider.isReadable(Map.class, Map.class, NO_ANNOTATIONS,
                MediaType.TEXT_PLAIN_TYPE)).isFalse();
        assertThat(provider.isWriteable(String.class, String.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> objectMapClass() {
        return (Class<Object>) (Class<?>) Map.class;
    }
}
