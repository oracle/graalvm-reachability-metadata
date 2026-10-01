/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_json_core;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonMapperTest {
    private final JsonMapper mapper = JsonMapper.createDefault();

    @Test
    void createDefaultDiscoversMapperAndReadsAndWritesJsonTrees() throws IOException {
        JsonNode input = mapper.readValue("{\"name\":\"Ada\",\"scores\":[3,5]}", JsonNode.class);

        assertThat(input.get("name").getStringValue()).isEqualTo("Ada");
        assertThat(input.get("scores").get(1).getIntValue()).isEqualTo(5);

        JsonNode output = mapper.writeValueToTree(Map.of("active", true, "count", 2));
        assertThat(output.get("active").getBooleanValue()).isTrue();
        assertThat(output.get("count").getIntValue()).isEqualTo(2);
        assertThat(mapper.readValue(mapper.writeValueAsBytes(output), JsonNode.class)).isEqualTo(output);
    }

    @Test
    void mapperReadsValuesThroughClassAndGenericArguments() throws IOException {
        String json = "{\"title\":\"notebook\",\"pages\":128}";

        Map<String, Object> asMap = mapper.readValue(json, Map.class);
        Map<String, Integer> typedMap = mapper.readValue(
                "{\"one\":1,\"two\":2}", Argument.mapOf(String.class, Integer.class));
        List<String> values = mapper.readValue("[\"first\",\"second\"]", Argument.listOf(String.class));

        assertThat(asMap).containsEntry("title", "notebook").containsEntry("pages", 128);
        assertThat(typedMap).containsEntry("one", 1).containsEntry("two", 2);
        assertThat(values).containsExactly("first", "second");
    }

    @Test
    void mapperReadsTypedValuesFromJsonTrees() throws IOException {
        JsonNode tree = mapper.writeValueToTree(Map.of("first", 21, "second", 34));

        Map<String, Integer> value = mapper.readValueFromTree(
                tree, Argument.mapOf(String.class, Integer.class));

        assertThat(value).containsEntry("first", 21).containsEntry("second", 34);
    }

    @Test
    void mapperWritesBytesStringsAndStreams() throws IOException {
        Map<String, Object> value = Map.of("message", "hello", "number", 42);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        mapper.writeValue(output, value);

        assertThat(new String(mapper.writeValueAsBytes(value), StandardCharsets.UTF_8))
                .contains("\"message\":\"hello\"")
                .contains("\"number\":42");
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("\"message\":\"hello\"")
                .contains("\"number\":42");
        assertThat(mapper.writeValueAsString(value)).contains("\"message\":\"hello\"");
    }

    @Test
    void mapperUpdatesExistingValuesFromJsonTrees() throws IOException {
        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("name", "Ada");
        destination.put("active", false);
        JsonNode update = mapper.readValue("{\"active\":true,\"score\":42}", JsonNode.class);

        mapper.updateValueFromTree(destination, update);

        assertThat(destination)
                .containsEntry("name", "Ada")
                .containsEntry("active", true)
                .containsEntry("score", 42);
    }

    @Test
    void mapperExposesStreamConfigurationAndFeatures() {
        JsonStreamConfig config = mapper.getStreamConfig()
                .withUseBigIntegerForInts(true)
                .withUseBigDecimalForFloats(true);

        assertThat(config.useBigIntegerForInts()).isTrue();
        assertThat(config.useBigDecimalForFloats()).isTrue();
        Optional<JsonFeatures> features = mapper.detectFeatures(AnnotationMetadata.EMPTY_METADATA);
        assertThat(features).isEmpty();
    }

    @Test
    void jsonNodeBuildersCreateScalarArrayAndObjectValues() {
        JsonNode array = JsonNode.createArrayNode(List.of(
                JsonNode.createStringNode("text"),
                JsonNode.createNumberNode(7),
                JsonNode.createBooleanNode(false),
                JsonNode.nullNode()));
        JsonNode object = JsonNode.createObjectNode(Map.of("items", array));

        assertThat(object.isObject()).isTrue();
        assertThat(object.get("items").isArray()).isTrue();
        assertThat(object.get("items").get(0).getStringValue()).isEqualTo("text");
        assertThat(object.get("items").get(1).getIntValue()).isEqualTo(7);
        assertThat(object.get("items").get(2).getBooleanValue()).isFalse();
        assertThat(object.get("items").get(3).isNull()).isTrue();
    }
}
