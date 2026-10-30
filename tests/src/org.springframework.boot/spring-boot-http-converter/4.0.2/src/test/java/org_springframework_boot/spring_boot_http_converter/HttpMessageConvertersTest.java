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
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConverters;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class HttpMessageConvertersTest {

    @Test
    void defaultConvertersWriteTextAndRetainJsonSupport() throws Exception {
        HttpMessageConverters configured = new HttpMessageConverters();
        List<HttpMessageConverter<?>> converters = configured.getConverters();
        StringHttpMessageConverter text = converters.stream()
                .filter(StringHttpMessageConverter.class::isInstance)
                .map(StringHttpMessageConverter.class::cast)
                .findFirst().orElseThrow();
        assertTrue(converters.stream().anyMatch(converter -> converter.canWrite(Map.class, MediaType.APPLICATION_JSON)));
        assertSame(converters.getFirst(), configured.iterator().next());

        BufferedOutput output = new BufferedOutput();
        text.write("delivery accepted", new MediaType("text", "plain", StandardCharsets.UTF_8), output);

        assertEquals("delivery accepted", output.body.toString(StandardCharsets.UTF_8));
        assertEquals(new MediaType("text", "plain", StandardCharsets.UTF_8), output.headers.getContentType());
    }

    @Test
    void additionalGsonConverterPrecedesEquivalentDefaultsForBodiesAndParts() throws Exception {
        GsonHttpMessageConverter gson = new GsonHttpMessageConverter();
        List<HttpMessageConverter<?>> converters = new HttpMessageConverters(gson).getConverters();
        assertGsonBeforeJackson(converters, gson);
        assertSame(gson, converters.stream()
                .filter(converter -> converter.canWrite(Map.class, MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow());

        AllEncompassingFormHttpMessageConverter form = converters.stream()
                .filter(AllEncompassingFormHttpMessageConverter.class::isInstance)
                .map(AllEncompassingFormHttpMessageConverter.class::cast)
                .findFirst().orElseThrow();
        assertGsonBeforeJackson(form.getPartConverters(), gson);
        assertSame(gson, form.getPartConverters().stream()
                .filter(converter -> converter.canWrite(Map.class, MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow());

        BufferedOutput output = new BufferedOutput();
        gson.write(Map.of("status", "queued"), MediaType.APPLICATION_JSON, output);

        assertEquals("""
                {"status":"queued"}""", output.body.toString(StandardCharsets.UTF_8));
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(output.headers.getContentType()));
    }

    private static void assertGsonBeforeJackson(List<HttpMessageConverter<?>> converters, GsonHttpMessageConverter gson) {
        HttpMessageConverter<?> jackson = converters.stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .findFirst().orElseThrow();
        int gsonIndex = converters.indexOf(gson);
        assertTrue(gsonIndex > 0, "The JSON replacement must not displace non-JSON defaults");
        assertEquals(gsonIndex + 1, converters.indexOf(jackson));
    }

    private static final class BufferedOutput implements HttpOutputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public OutputStream getBody() {
            return body;
        }
    }
}
