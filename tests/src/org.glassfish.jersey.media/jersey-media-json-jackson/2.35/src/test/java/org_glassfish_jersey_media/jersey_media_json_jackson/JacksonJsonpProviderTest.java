/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_media.jersey_media_json_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.annotation.JSONP;
import org.junit.jupiter.api.Test;

public class JacksonJsonpProviderTest {
    private static final MediaType APPLICATION_JAVASCRIPT = MediaType.valueOf(JacksonJsonProvider.MIME_JAVASCRIPT);

    @Test
    void jsonpFunctionNameWrapsSerializedJson() throws IOException {
        JacksonJsonProvider provider = new JacksonJsonProvider();
        provider.setJSONPFunctionName("handlePayload");

        String json = writePayload(provider, new Annotation[0], APPLICATION_JAVASCRIPT);

        assertThat(json).startsWith("handlePayload(");
        assertThat(json).endsWith(")");
        assertThat(json).contains("\"message\":\"ready\"", "\"count\":3");
    }

    @Test
    void jsonpAnnotationCanCustomizePrefixAndSuffix() throws IOException {
        JacksonJsonProvider provider = new JacksonJsonProvider();
        Annotation[] annotations = {new JsonpAnnotation("", "while(1);consume(", ");")};

        String json = writePayload(provider, annotations, APPLICATION_JAVASCRIPT);

        assertThat(json).startsWith("while(1);consume(");
        assertThat(json).endsWith(");");
        assertThat(json).contains("\"message\":\"ready\"", "\"count\":3");
    }

    @Test
    void providerRecognizesJavascriptMediaTypes() {
        JacksonJsonProvider provider = new JacksonJsonProvider();

        assertThat(provider.isWriteable(JsonpPayload.class, JsonpPayload.class, new Annotation[0], APPLICATION_JAVASCRIPT))
                .isTrue();
        assertThat(provider.isWriteable(JsonpPayload.class, JsonpPayload.class, new Annotation[0],
                MediaType.valueOf(JacksonJsonProvider.MIME_JAVASCRIPT_MS)))
                .isTrue();
    }

    private static String writePayload(JacksonJsonProvider provider, Annotation[] annotations, MediaType mediaType)
            throws IOException {
        JsonpPayload payload = new JsonpPayload("ready", 3);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        provider.writeTo(payload, JsonpPayload.class, JsonpPayload.class, annotations, mediaType, headers, output);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    public static final class JsonpPayload {
        public String message;
        public int count;

        public JsonpPayload() {
        }

        JsonpPayload(String message, int count) {
            this.message = message;
            this.count = count;
        }
    }

    private static final class JsonpAnnotation implements JSONP {
        private final String value;
        private final String prefix;
        private final String suffix;

        private JsonpAnnotation(String value, String prefix, String suffix) {
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
}
