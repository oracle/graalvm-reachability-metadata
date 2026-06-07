/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt.json_schema_validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.resource.ClasspathSchemaLoader;
import com.networknt.schema.resource.InputStreamSource;
import org.junit.jupiter.api.Test;

public class ClasspathSchemaLoaderTest {
    @Test
    void loadsClasspathSchemaWhenResourcePathStartsWithSlash() throws Exception {
        ClasspathSchemaLoader loader = new ClasspathSchemaLoader();
        InputStreamSource source = loader.getSchema(
                AbsoluteIri.of("classpath:/schemas/classpath-loader-schema.json"));

        assertThat(source).isNotNull();
        try (InputStream input = source.getInputStream()) {
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(schema).contains("$schema", "type", "object");
        }
    }

    @Test
    void loadsClasspathSchemaWhenResourcePathHasNoLeadingSlash() throws Exception {
        ClasspathSchemaLoader loader = new ClasspathSchemaLoader();
        InputStreamSource source = loader.getSchema(
                AbsoluteIri.of("resource:schemas/classpath-loader-schema.json"));

        assertThat(source).isNotNull();
        try (InputStream input = source.getInputStream()) {
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(schema).contains("properties", "name", "string");
        }
    }
}
