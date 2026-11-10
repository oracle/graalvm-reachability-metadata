/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_dataformat.jackson_dataformat_yaml;

import java.io.StringWriter;
import java.util.ServiceLoader;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class YAMLFactoryTest {

    @Test
    void serviceDiscoveredFactoryWritesAndReadsYaml() throws Exception {
        JsonNode configuration = new YAMLMapper().readTree("""
                format: YAML
                service: catalog
                replicas: 3
                """);
        JsonFactory factory = ServiceLoader.load(JsonFactory.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> configuration.path("format").asText().equals(candidate.getFormatName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No YAML factory provider found"));

        StringWriter output = new StringWriter();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeStringField("service", configuration.path("service").asText());
            generator.writeNumberField("replicas", configuration.path("replicas").asInt());
            generator.writeEndObject();
        }

        assertThat(output.toString()).contains("service:").contains("replicas: 3");
        try (JsonParser parser = factory.createParser(output.toString())) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("service");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("catalog");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("replicas");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
            assertThat(parser.getIntValue()).isEqualTo(3);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }
    }
}
