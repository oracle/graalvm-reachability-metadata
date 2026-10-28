/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class HttpMessageConvertersTest {

    @Test
    void defaultsReadAndWriteJson() throws Exception {
        HttpMessageConverters converters = new HttpMessageConverters();
        MappingJackson2HttpMessageConverter json = converters.getConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .findFirst().orElseThrow();
        MemoryInputMessage input = new MemoryInputMessage("""
                {"name":"orchard","count":4}
                """.getBytes(StandardCharsets.UTF_8));
        input.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Object decoded = json.read(Map.class, input);
        assertThat(decoded).isEqualTo(Map.of("name", "orchard", "count", 4));

        MemoryOutputMessage output = new MemoryOutputMessage();
        json.write(Map.of("ready", true), MediaType.APPLICATION_JSON, output);
        assertThat(output.getBodyAsString(StandardCharsets.UTF_8)).isEqualTo("{\"ready\":true}");
    }

    @Test
    void gsonPrecedesEquivalentJacksonConverterForBodiesAndMultipartParts() throws Exception {
        GsonHttpMessageConverter gson = new GsonHttpMessageConverter();
        HttpMessageConverters converters = new HttpMessageConverters(gson);
        List<HttpMessageConverter<?>> registered = converters.getConverters();
        HttpMessageConverter<?> firstJson = registered.stream()
                .filter(converter -> converter.canWrite(Map.class, MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow();
        assertThat(firstJson).isSameAs(gson);
        AllEncompassingFormHttpMessageConverter form = registered.stream()
                .filter(AllEncompassingFormHttpMessageConverter.class::isInstance)
                .map(AllEncompassingFormHttpMessageConverter.class::cast)
                .findFirst().orElseThrow();
        HttpMessageConverter<?> firstJsonPart = form.getPartConverters().stream()
                .filter(converter -> converter.canWrite(Map.class, MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow();
        assertThat(firstJsonPart).isSameAs(gson);

        MemoryOutputMessage output = new MemoryOutputMessage();
        gson.write(Map.of("message", "hello"), MediaType.APPLICATION_JSON, output);
        assertThat(output.getBodyAsString(StandardCharsets.UTF_8)).isEqualTo("{\"message\":\"hello\"}");
    }

    @Test
    void explicitConverterListOmitsDefaultsAndWritesText() throws Exception {
        StringHttpMessageConverter text = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        HttpMessageConverters converters = new HttpMessageConverters(false, List.of(text));
        assertThat(converters.getConverters()).containsExactly(text);
        assertThat(converters).containsExactly(text);

        MemoryOutputMessage output = new MemoryOutputMessage();
        text.write("hello orchard", MediaType.TEXT_PLAIN, output);
        assertThat(output.getBodyAsString(StandardCharsets.UTF_8)).isEqualTo("hello orchard");
    }

    private static final class MemoryInputMessage implements HttpInputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final InputStream body;

        private MemoryInputMessage(byte[] content) {
            this.body = new ByteArrayInputStream(content);
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }

        @Override
        public InputStream getBody() {
            return this.body;
        }
    }

    private static final class MemoryOutputMessage implements HttpOutputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }

        @Override
        public OutputStream getBody() {
            return this.body;
        }

        private String getBodyAsString(Charset charset) {
            return this.body.toString(charset);
        }
    }
}
