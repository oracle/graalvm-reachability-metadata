/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_jaxrs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jackson_jaxrsTest {
    @Test
    void providerReadsAndWritesJsonMessageBodies() throws Exception {
        JacksonJsonProvider provider = new JacksonJsonProvider();
        Annotation[] annotations = new Annotation[0];
        MediaType mediaType = null;
        String json = """
                {"message":"hello"}
                """.trim();

        assertThat(provider.isReadable(Object.class, Object.class, annotations, mediaType)).isTrue();
        assertThat(provider.isWriteable(Object.class, Object.class, annotations, mediaType)).isTrue();
        try (ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
                        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Object decoded = provider.readFrom(Object.class, Object.class, annotations, mediaType, null, input);
            assertThat(decoded).isEqualTo(Map.of("message", "hello"));

            provider.writeTo(decoded, Object.class, Object.class, annotations, mediaType, null, output);
            assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo(json);
        }
    }
}
