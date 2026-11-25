/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_http_converter;

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
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
public class HttpMessageConvertersTest {
    @Test
    void preservesTypeConstrainedConverterAheadOfDefaults() throws Exception {
        TypeConstrainedJacksonJsonHttpMessageConverter constrained =
                new TypeConstrainedJacksonJsonHttpMessageConverter(Map.class);
        List<HttpMessageConverter<?>> converters = new HttpMessageConverters(constrained).getConverters();

        assertSame(constrained, converters.getFirst());
        assertTrue(converters.stream().anyMatch(converter -> converter instanceof JacksonJsonHttpMessageConverter));
        AllEncompassingFormHttpMessageConverter form = converters.stream()
                .filter(AllEncompassingFormHttpMessageConverter.class::isInstance)
                .map(AllEncompassingFormHttpMessageConverter.class::cast).findFirst().orElseThrow();
        assertSame(constrained, form.getPartConverters().getFirst());

        BufferedOutput output = new BufferedOutput();
        assertTrue(constrained.canWrite(Map.class, MediaType.APPLICATION_JSON));
        constrained.write(Map.of("status", "ready"), MediaType.APPLICATION_JSON, output);
        assertEquals("{\"status\":\"ready\"}", output.body.toString(StandardCharsets.UTF_8));
        assertTrue(output.headers.getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void insertsEquivalentJsonConverterImmediatelyBeforeJacksonIncludingMultipartParts() throws Exception {
        GsonHttpMessageConverter gson = new GsonHttpMessageConverter();
        List<HttpMessageConverter<?>> converters = new HttpMessageConverters(gson).getConverters();

        assertImmediatelyBeforeJackson(converters, gson);
        AllEncompassingFormHttpMessageConverter form = converters.stream()
                .filter(AllEncompassingFormHttpMessageConverter.class::isInstance)
                .map(AllEncompassingFormHttpMessageConverter.class::cast).findFirst().orElseThrow();
        assertImmediatelyBeforeJackson(form.getPartConverters(), gson);

        BufferedOutput output = new BufferedOutput();
        gson.write(Map.of("message", "converted"), MediaType.APPLICATION_JSON, output);
        assertEquals("{\"message\":\"converted\"}", output.body.toString(StandardCharsets.UTF_8));
        assertTrue(output.headers.getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
    }

    private static void assertImmediatelyBeforeJackson(List<HttpMessageConverter<?>> converters,
            GsonHttpMessageConverter gson) {
        HttpMessageConverter<?> jackson = converters.stream()
                .filter(converter -> converter.getClass() == JacksonJsonHttpMessageConverter.class)
                .findFirst().orElseThrow();
        int jacksonIndex = converters.indexOf(jackson);
        assertTrue(jacksonIndex > 0);
        assertSame(gson, converters.get(jacksonIndex - 1));
    }

    private static final class BufferedOutput implements HttpOutputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        @Override
        public OutputStream getBody() {
            return this.body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }
    }
}
