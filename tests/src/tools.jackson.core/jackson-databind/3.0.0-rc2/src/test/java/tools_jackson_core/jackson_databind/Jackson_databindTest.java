/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_core.jackson_databind;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SequenceWriter;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ser.std.StdSerializer;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Jackson_databindTest {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Test
    void roundTripsNestedMapsWithTypedReaderWriter() throws JacksonException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "espresso");
        payload.put("shots", 2);
        payload.put("available", true);
        payload.put("tags", List.of("hot", "small"));
        payload.put("details", Map.of("origin", "Colombia", "ratio", BigDecimal.valueOf(18.5)));

        ObjectWriter writer = MAPPER.writerFor(new TypeReference<Map<String, Object>>() { });
        String json = writer.writeValueAsString(payload);

        ObjectReader reader = MAPPER.readerFor(new TypeReference<Map<String, Object>>() { })
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        Map<String, Object> result = reader.readValue(json);

        assertThat(result).containsEntry("name", "espresso");
        assertThat(result).containsEntry("shots", 2);
        assertThat(result).containsEntry("available", true);
        assertThat(result.get("tags")).isEqualTo(List.of("hot", "small"));
        assertThat(result.get("details")).isInstanceOf(Map.class);
        Map<?, ?> details = (Map<?, ?>) result.get("details");
        assertThat(details.get("origin")).isEqualTo("Colombia");
        assertThat(details.get("ratio")).isEqualTo(BigDecimal.valueOf(18.5));
    }

    @Test
    void buildsQueriesAndMutatesJsonTrees() throws JacksonException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("orderId", 17);
        root.withObjectProperty("customer").put("name", "Ada");
        ArrayNode items = root.withArrayProperty("items");
        items.addObject().put("sku", "beans").put("quantity", 2);
        items.addObject().put("sku", "grinder").put("quantity", 1);
        items.insert(1, "gift-wrap");

        assertThat(root.at("/customer/name").asString()).isEqualTo("Ada");
        assertThat(root.path("items").path(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(root.path("items").path(9).isMissingNode()).isTrue();

        items.remove(1);
        root.withObjectProperty("shipping").put("expedited", true);
        String json = MAPPER.writeValueAsString(root);
        JsonNode reparsed = MAPPER.readTree(json);

        assertThat(reparsed.at("/shipping/expedited").asBoolean()).isTrue();
        assertThat(reparsed.at("/items/1/sku").asString()).isEqualTo("grinder");
        assertThat(reparsed.has("orderId")).isTrue();
    }

    @Test
    void convertsBetweenTreesAndParameterizedJavaTypes() throws JacksonException {
        ObjectNode root = MAPPER.createObjectNode();
        root.withArrayProperty("scores").add(9).add(10).add(8);
        root.withObjectProperty("metadata").put("source", "integration-test");

        JavaType mapType = MAPPER.getTypeFactory().constructMapType(
                LinkedHashMap.class,
                String.class,
                Object.class);
        Map<String, Object> asMap = MAPPER.treeToValue(root, mapType);

        assertThat(asMap).containsKeys("scores", "metadata");
        assertThat(asMap.get("scores")).isEqualTo(List.of(9, 10, 8));

        JsonNode roundTripped = MAPPER.valueToTree(asMap);
        assertThat(roundTripped.at("/metadata/source").asString()).isEqualTo("integration-test");
        assertThat(roundTripped.at("/scores/2").asInt()).isEqualTo(8);
    }

    @Test
    void streamsSequencesWithSequenceWriterAndMappingIterator() throws JacksonException {
        StringWriter output = new StringWriter();
        try (SequenceWriter sequenceWriter = MAPPER.writerFor(Integer.class).writeValuesAsArray(output)) {
            sequenceWriter.write(3);
            sequenceWriter.write(1);
            sequenceWriter.write(4);
        }

        String json = output.toString();
        List<Integer> values = new ArrayList<>();
        try (MappingIterator<Integer> iterator = MAPPER.readerFor(Integer.class).readValues(json)) {
            while (iterator.hasNextValue()) {
                values.add(iterator.nextValue());
            }
        }

        assertThat(json).isEqualTo("[3,1,4]");
        assertThat(values).containsExactly(3, 1, 4);
    }

    @Test
    void updatesExistingContainersWithoutReplacingUnchangedEntries() throws JacksonException {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("status", "new");
        target.put("attempts", 1);
        target.put("notes", new ArrayList<>(List.of("created")));

        Map<String, Object> patched = MAPPER.readerForUpdating(target)
                .readValue("{\"status\":\"processing\",\"owner\":\"worker-1\"}");

        assertThat(patched).isSameAs(target);
        assertThat(patched)
                .containsEntry("status", "processing")
                .containsEntry("attempts", 1)
                .containsEntry("owner", "worker-1");
        assertThat(patched.get("notes")).isEqualTo(List.of("created"));
    }

    @Test
    void honorsPerReaderAndPerWriterFeatureConfiguration() throws JacksonException {
        Map<String, Integer> unordered = new LinkedHashMap<>();
        unordered.put("b", 2);
        unordered.put("a", 1);

        String sortedPrettyJson = MAPPER.writer()
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .with(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(unordered);

        assertThat(sortedPrettyJson).contains("\n");
        assertThat(sortedPrettyJson.indexOf("\"a\""))
                .isLessThan(sortedPrettyJson.indexOf("\"b\""));

        ObjectReader strictReader = MAPPER.readerFor(new TypeReference<Map<String, Integer>>() { })
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        assertThatThrownBy(() -> strictReader.readValue("{\"a\":1} {\"b\":2}"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void registersCustomModuleForExplicitScalarType() throws JacksonException {
        SimpleModule module = new SimpleModule("token-module");
        module.addSerializer(Token.class, new TokenSerializer());
        module.addDeserializer(Token.class, new TokenDeserializer());
        ObjectMapper mapper = JsonMapper.builder().addModule(module).build();

        Token token = new Token("coffee", 42);
        String json = mapper.writeValueAsString(token);
        Token result = mapper.readValue(json, Token.class);

        assertThat(json).isEqualTo("\"coffee#42\"");
        assertThat(result).isEqualTo(token);
    }

    @Test
    void bindsArraysListsAndPrimitiveArraysByJavaType() throws JacksonException {
        JavaType listOfStrings = MAPPER.getTypeFactory().constructCollectionType(List.class, String.class);
        List<String> names = MAPPER.readValue("[\"alpha\",\"beta\"]", listOfStrings);

        int[] numbers = MAPPER.readerFor(int[].class).readValue("[5,8,13]");
        String encodedNames = MAPPER.writerFor(listOfStrings).writeValueAsString(names);

        assertThat(names).containsExactly("alpha", "beta");
        assertThat(numbers).containsExactly(5, 8, 13);
        assertThat(encodedNames).isEqualTo("[\"alpha\",\"beta\"]");
    }

    public static final class Token {
        private final String prefix;
        private final int number;

        public Token(String prefix, int number) {
            this.prefix = prefix;
            this.number = number;
        }

        String prefix() {
            return prefix;
        }

        int number() {
            return number;
        }

        static Token parse(String value) {
            int separator = value.indexOf('#');
            return new Token(value.substring(0, separator), Integer.parseInt(value.substring(separator + 1)));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Token token)) {
                return false;
            }
            return number == token.number && prefix.equals(token.prefix);
        }

        @Override
        public int hashCode() {
            return 31 * prefix.hashCode() + number;
        }
    }

    public static final class TokenSerializer extends StdSerializer<Token> {
        public TokenSerializer() {
            super(Token.class);
        }

        @Override
        public void serialize(Token value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeString(value.prefix() + "#" + value.number());
        }
    }

    public static final class TokenDeserializer extends StdDeserializer<Token> {
        public TokenDeserializer() {
            super(Token.class);
        }

        @Override
        public Token deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            return Token.parse(parser.getValueAsString());
        }
    }
}
