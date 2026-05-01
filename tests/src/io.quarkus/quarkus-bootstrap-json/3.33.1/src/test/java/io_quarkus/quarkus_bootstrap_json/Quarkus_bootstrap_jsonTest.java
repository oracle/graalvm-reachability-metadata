/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.bootstrap.json.Json;
import io.quarkus.bootstrap.json.JsonArray;
import io.quarkus.bootstrap.json.JsonBoolean;
import io.quarkus.bootstrap.json.JsonDouble;
import io.quarkus.bootstrap.json.JsonInteger;
import io.quarkus.bootstrap.json.JsonMember;
import io.quarkus.bootstrap.json.JsonNull;
import io.quarkus.bootstrap.json.JsonObject;
import io.quarkus.bootstrap.json.JsonReader;
import io.quarkus.bootstrap.json.JsonString;
import io.quarkus.bootstrap.json.JsonTransform;
import io.quarkus.bootstrap.json.JsonValue;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class Quarkus_bootstrap_jsonTest {
    @Test
    void readsJsonValuesAndExposesTypedValueObjects() {
        String document = """
                {
                  "text": "line\\nquote=\\\" slash=\\\\ unicode=\\u263A",
                  "active": true,
                  "disabled": false,
                  "nothing": null,
                  "small": 42,
                  "large": 9223372036854775807,
                  "decimal": -12.75,
                  "items": ["alpha", 7, false, null, {"nested": "value"}]
                }
                """;

        JsonObject object = JsonReader.of(document).read();

        JsonString text = object.get("text");
        assertThat(text.value()).isEqualTo("line\nquote=\" slash=\\ unicode=☺");
        JsonBoolean active = object.get("active");
        JsonBoolean disabled = object.get("disabled");
        assertThat(active).isSameAs(JsonBoolean.TRUE);
        assertThat(active.value()).isTrue();
        assertThat(disabled).isSameAs(JsonBoolean.FALSE);
        assertThat(disabled.value()).isFalse();
        assertThat((JsonNull) object.get("nothing")).isSameAs(JsonNull.INSTANCE);
        JsonInteger small = object.get("small");
        JsonInteger large = object.get("large");
        assertThat(small.intValue()).isEqualTo(42);
        assertThat(small.longValue()).isEqualTo(42L);
        assertThat(small.toString()).isEqualTo("42");
        assertThat(large.longValue()).isEqualTo(Long.MAX_VALUE);
        JsonDouble decimal = object.get("decimal");
        assertThat(decimal.value()).isEqualTo(-12.75);

        JsonArray items = object.get("items");
        assertThat(items.size()).isEqualTo(5);
        assertThat(items.value()).hasSize(5);
        assertThat(items.value().get(0)).isInstanceOf(JsonString.class);
        assertThat(((JsonString) items.value().get(0)).value()).isEqualTo("alpha");
        assertThat(((JsonInteger) items.value().get(1)).intValue()).isEqualTo(7);
        assertThat(items.value().get(2)).isSameAs(JsonBoolean.FALSE);
        assertThat(items.value().get(3)).isSameAs(JsonNull.INSTANCE);
        JsonObject nested = (JsonObject) items.value().get(4);
        assertThat(((JsonString) nested.get("nested")).value()).isEqualTo("value");

        Map<String, JsonValue> membersByName = object.members().stream()
                .collect(Collectors.toMap(member -> member.attribute().value(), JsonMember::value));
        assertThat(membersByName.keySet()).contains("text", "active", "items");
    }

    @Test
    void writesNestedJsonAndEscapesStrings() throws IOException {
        Json.JsonArrayBuilder tags = Json.array(3)
                .add("native")
                .add("json")
                .add("escape \"quotes\" and backslash \\ and tab\t");
        Json.JsonObjectBuilder metadata = Json.object()
                .put("enabled", true)
                .put("count", 2)
                .put("big", 3_000_000_000L);
        Json.JsonObjectBuilder object = Json.object(8)
                .put("name", "quarkus-bootstrap-json")
                .put("tags", tags)
                .put("metadata", metadata)
                .put("ignoredNull", (String) null);

        String json = render(object);
        JsonObject parsed = JsonReader.of(json).read();

        assertThat(((JsonString) parsed.get("name")).value()).isEqualTo("quarkus-bootstrap-json");
        assertThat((JsonValue) parsed.get("ignoredNull")).isNull();
        JsonArray parsedTags = parsed.get("tags");
        List<String> tagValues = parsedTags.<JsonString>stream().map(JsonString::value).toList();
        assertThat(tagValues).containsExactly("native", "json", "escape \"quotes\" and backslash \\ and tab\t");
        JsonObject parsedMetadata = parsed.get("metadata");
        assertThat(((JsonBoolean) parsedMetadata.get("enabled")).value()).isTrue();
        assertThat(((JsonInteger) parsedMetadata.get("count")).intValue()).isEqualTo(2);
        assertThat(((JsonInteger) parsedMetadata.get("big")).longValue()).isEqualTo(3_000_000_000L);
        assertThat(json).contains("\\\"quotes\\\"");
        assertThat(json).contains("\\\\");
        assertThat(json).contains("\\u0009");
    }

    @Test
    void buildersImplementMutableMapAndCollectionContracts() throws IOException {
        Json.JsonObjectBuilder object = Json.object();
        assertThat(object.isEmpty()).isTrue();
        assertThat(object.put("first", "one")).isSameAs(object);
        assertThat(object.put("second", (Object) 2L)).isNull();
        Map<String, Object> additionalValues = new LinkedHashMap<>();
        additionalValues.put("third", true);
        additionalValues.put("nullValue", null);
        object.putAll(additionalValues);

        assertThat(object).hasSize(3);
        assertThat(object.has("first")).isTrue();
        assertThat(object.containsKey("second")).isTrue();
        assertThat(object.containsValue(2L)).isTrue();
        assertThat(object.get("first")).isEqualTo("one");
        assertThat(object.keySet()).contains("first", "second", "third");
        assertThat(object.values()).contains("one", 2L, true);
        assertThat(object.entrySet()).extracting(entry -> entry.getKey()).contains("first", "second", "third");
        assertThat(object.remove("third")).isEqualTo(true);
        assertThat(object).hasSize(2);

        Json.JsonArrayBuilder array = Json.array();
        assertThat(array.isEmpty()).isTrue();
        assertThat(array.add("first")).isSameAs(array);
        assertThat(array.add((Object) null)).isFalse();
        assertThat(array.addAll(Arrays.asList("second", null, 3L))).isFalse();
        assertThat(array).hasSize(3);
        assertThat(array.contains("second")).isTrue();
        assertThat(array.containsAll(List.of("first", "second"))).isTrue();
        assertThat(array.toArray()).containsExactly("first", "second", 3L);
        assertThat(array.toArray(new Object[0])).containsExactly("first", "second", 3L);
        assertThat(array.remove("second")).isTrue();
        assertThat(array).containsExactly("first", 3L);
        assertThat(array.retainAll(List.of("first"))).isTrue();
        assertThat(array).containsExactly("first");
        assertThat(array.removeAll(List.of("first"))).isTrue();
        assertThat(array).isEmpty();

        array.add("after-clear");
        object.put("array", array);
        JsonObject parsed = JsonReader.of(render(object)).read();
        assertThat(((JsonString) parsed.get("first")).value()).isEqualTo("one");
        JsonArray parsedArray = parsed.get("array");
        assertThat(parsedArray.<JsonString>stream().map(JsonString::value).toList()).containsExactly("after-clear");

        object.clear();
        array.clear();
        assertThat(object).isEmpty();
        assertThat(array).isEmpty();
    }

    @Test
    void ignoreEmptyBuildersOmitsNestedEmptyObjectsAndArrays() throws IOException {
        Json.JsonObjectBuilder retainedEmpty = Json.object()
                .put("emptyObject", Json.object())
                .put("emptyArray", Json.array())
                .put("nonEmpty", Json.array().add("value"));
        Json.JsonObjectBuilder ignoredEmpty = Json.object(true)
                .put("emptyObject", Json.object())
                .put("emptyArray", Json.array())
                .put("nonEmpty", Json.array().add("value"));
        Json.JsonArrayBuilder ignoredEmptyArrayElements = Json.array(true)
                .add(Json.object())
                .add(Json.array())
                .add(Json.object().put("kept", true));

        JsonObject retainedParsed = JsonReader.of(render(retainedEmpty)).read();
        assertThat((JsonObject) retainedParsed.get("emptyObject")).isNotNull();
        assertThat((JsonArray) retainedParsed.get("emptyArray")).isNotNull();
        assertThat(((JsonArray) retainedParsed.get("nonEmpty")).size()).isEqualTo(1);

        JsonObject ignoredParsed = JsonReader.of(render(ignoredEmpty)).read();
        assertThat((JsonValue) ignoredParsed.get("emptyObject")).isNull();
        assertThat((JsonValue) ignoredParsed.get("emptyArray")).isNull();
        assertThat(((JsonArray) ignoredParsed.get("nonEmpty")).size()).isEqualTo(1);

        JsonArray arrayParsed = JsonReader.of(render(ignoredEmptyArrayElements)).read();
        assertThat(arrayParsed.size()).isEqualTo(1);
        JsonObject onlyElement = (JsonObject) arrayParsed.value().get(0);
        assertThat(((JsonBoolean) onlyElement.get("kept")).value()).isTrue();
    }

    @Test
    void transformsObjectGraphsWithDroppingPredicate() throws IOException {
        JsonObject source = JsonReader.of("""
                {
                  "keep": "root",
                  "password": "secret",
                  "nested": {"keep": "child", "password": "hidden"},
                  "numbers": [1, 2, 3, {"password": "hidden", "name": "kept"}]
                }
                """).read();
        JsonTransform dropPasswordsAndTwos = JsonTransform.dropping(value -> {
            if (value instanceof JsonMember member) {
                return "password".equals(member.attribute().value());
            }
            return value instanceof JsonInteger integer && integer.intValue() == 2;
        });

        Json.JsonObjectBuilder transformedObject = Json.object(true);
        transformedObject.transform(source, dropPasswordsAndTwos);
        JsonObject parsedObject = JsonReader.of(render(transformedObject)).read();

        assertThat(((JsonString) parsedObject.get("keep")).value()).isEqualTo("root");
        assertThat((JsonValue) parsedObject.get("password")).isNull();
        JsonObject nested = parsedObject.get("nested");
        assertThat(((JsonString) nested.get("keep")).value()).isEqualTo("child");
        assertThat((JsonValue) nested.get("password")).isNull();
        JsonArray numbers = parsedObject.get("numbers");
        assertThat(numbers.value()).hasSize(3);
        assertThat(numbers.value().stream()
                .filter(JsonInteger.class::isInstance)
                .map(JsonInteger.class::cast)
                .map(JsonInteger::intValue)
                .toList()).containsExactly(1, 3);
        JsonObject objectInsideArray = (JsonObject) numbers.value().get(2);
        assertThat((JsonValue) objectInsideArray.get("password")).isNull();
        assertThat(((JsonString) objectInsideArray.get("name")).value()).isEqualTo("kept");

        JsonArray sourceArray = JsonReader.of("[1, 2, {\"password\": \"hidden\", \"name\": \"kept\"}]").read();
        Json.JsonArrayBuilder transformedArray = Json.array(true);
        transformedArray.transform(sourceArray, dropPasswordsAndTwos);
        JsonArray parsedArray = JsonReader.of(render(transformedArray)).read();
        assertThat(parsedArray.value()).hasSize(2);
        assertThat(((JsonInteger) parsedArray.value().get(0)).intValue()).isEqualTo(1);
        assertThat((JsonValue) ((JsonObject) parsedArray.value().get(1)).get("password")).isNull();
    }

    @Test
    void valueObjectsSupportConstructionEqualityAndIteration() {
        JsonString firstName = new JsonString("name");
        JsonString equivalentName = new JsonString("name");
        JsonInteger integer = new JsonInteger(123L);
        JsonMember member = new JsonMember(firstName, integer);
        Map<JsonString, JsonValue> objectValues = new LinkedHashMap<>();
        objectValues.put(firstName, integer);
        JsonObject object = new JsonObject(objectValues);
        List<JsonValue> arrayValues = List.of(new JsonString("a"), JsonBoolean.TRUE, JsonNull.INSTANCE);
        JsonArray array = new JsonArray(arrayValues);

        assertThat(firstName).isEqualTo(equivalentName);
        assertThat(firstName.hashCode()).isEqualTo(equivalentName.hashCode());
        assertThat(firstName.toString()).isEqualTo("name");
        assertThat(member.attribute()).isSameAs(firstName);
        assertThat(member.value()).isSameAs(integer);
        assertThat((JsonValue) object.get("name")).isSameAs(integer);
        assertThat(object.members()).hasSize(1);
        assertThat(array.size()).isEqualTo(3);
        assertThat(array.value()).containsExactly(new JsonString("a"), JsonBoolean.TRUE, JsonNull.INSTANCE);

        Set<String> visitedObjectMembers = object.members().stream()
                .map(JsonMember::attribute)
                .map(JsonString::value)
                .collect(Collectors.toSet());
        assertThat(visitedObjectMembers).containsExactly("name");
        assertThat(array.<JsonValue>stream().toList()).containsExactlyElementsOf(array.value());
    }

    @Test
    void rejectsInvalidInputAndUnsupportedOutputValues() {
        assertThatThrownBy(() -> JsonReader.of("").read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to fully read json value");
        assertThatThrownBy(() -> JsonReader.of("?").read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown start character");
        assertThatThrownBy(() -> JsonReader.of("{\"name\" \"value\"}").read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected : after attribute");
        assertThatThrownBy(() -> JsonReader.of("[1, 2").read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Json array ended without ]");
        assertThatThrownBy(() -> JsonReader.of("{\"name\": true").read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Json object ended without }");
        assertThatThrownBy(() -> JsonReader.of("\"unterminated").read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("String not closed");
        String jsonWithControlCharacter = "\"bad" + Character.toString((char) 1) + "char\"";
        assertThatThrownBy(() -> JsonReader.of(jsonWithControlCharacter).read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Control characters not allowed");
        assertThatThrownBy(() -> JsonReader.of("truth").read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to read json constant");
        assertThatThrownBy(() -> Json.object().put(null, "value"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> {
            Json.JsonObjectBuilder object = Json.object();
            object.put("unsupported", new Object());
            render(object);
        }).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported value type");
    }

    private static String render(Json.JsonBuilder<?> builder) throws IOException {
        StringBuilder output = new StringBuilder();
        builder.appendTo(output);
        return output.toString();
    }
}
