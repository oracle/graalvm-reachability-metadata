/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConverters;
import org.springframework.hateoas.server.mvc.TypeConstrainedJacksonJsonHttpMessageConverter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpMessageConvertersTest {
    @Test
    void mergesEquivalentJsonConvertersWithoutReplacingConstrainedConverters() throws Exception {
        TypeConstrainedJacksonJsonHttpMessageConverter constrained =
                new TypeConstrainedJacksonJsonHttpMessageConverter(Map.class);
        GsonHttpMessageConverter gson = new GsonHttpMessageConverter();
        List<HttpMessageConverter<?>> converters = new HttpMessageConverters(constrained, gson).getConverters();
        assertEquals(constrained, converters.getFirst());
        int jacksonIndex = -1;
        for (int index = 0; index < converters.size(); index++) {
            if (converters.get(index).getClass() == JacksonJsonHttpMessageConverter.class) {
                jacksonIndex = index;
            }
        }
        assertTrue(jacksonIndex > 0);
        assertEquals(gson, converters.get(jacksonIndex - 1));
        assertTrue(constrained.canWrite(Map.class, MediaType.APPLICATION_JSON));
        BufferedOutput output = new BufferedOutput();
        gson.write(Map.of("message", "converted"), MediaType.APPLICATION_JSON, output);
        assertEquals("{\"message\":\"converted\"}", output.body.toString(StandardCharsets.UTF_8));
        assertEquals(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8), output.headers.getContentType());
    }

    private static final class BufferedOutput implements HttpOutputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        @Override
        public OutputStream getBody() {
            return body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
