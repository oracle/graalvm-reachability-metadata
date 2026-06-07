/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt.json_schema_validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.resource.ClasspathResourceLoader;
import com.networknt.schema.resource.InputStreamSource;
import org.junit.jupiter.api.Test;

public class ClasspathResourceLoaderTest {
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              }
            }
            """;

    @Test
    void loadsClasspathSchemaWhenResourcePathStartsWithSlash() throws Exception {
        ClasspathResourceLoader loader = new ClasspathResourceLoader(ClasspathResourceLoaderTest::schemaClassLoader);
        InputStreamSource source = loader.getResource(
                AbsoluteIri.of("classpath:/schemas/classpath-loader-schema.json"));

        assertThat(source).isNotNull();
        try (InputStream input = source.getInputStream()) {
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(schema).contains("$schema", "type", "object");
        }
    }

    @Test
    void loadsClasspathSchemaWhenResourcePathHasNoLeadingSlash() throws Exception {
        ClasspathResourceLoader loader = new ClasspathResourceLoader(ClasspathResourceLoaderTest::schemaClassLoader);
        InputStreamSource source = loader.getResource(
                AbsoluteIri.of("resource:schemas/classpath-loader-schema.json"));

        assertThat(source).isNotNull();
        try (InputStream input = source.getInputStream()) {
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(schema).contains("properties", "name", "string");
        }
    }

    private static ClassLoader schemaClassLoader() {
        return new ClassLoader(ClasspathResourceLoaderTest.class.getClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if ("schemas/classpath-loader-schema.json".equals(name)) {
                    return new ByteArrayInputStream(SCHEMA.getBytes(StandardCharsets.UTF_8));
                }
                return super.getResourceAsStream(name);
            }
        };
    }
}
