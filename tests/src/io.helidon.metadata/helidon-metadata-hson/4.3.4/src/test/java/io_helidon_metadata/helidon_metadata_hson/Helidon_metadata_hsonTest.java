/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_metadata.helidon_metadata_hson;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import io.helidon.metadata.hson.Hson;
import io.helidon.metadata.hson.HsonException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Helidon_metadata_hsonTest {
    @Test
    void parseNestedDocumentAndExposeTypedValues() {
        Hson.Struct struct = parseStruct("""
                {
                  "name": "helidon",
                  "enabled": true,
                  "count": 7,
                  "ratio": 2.5,
                  "nothing": null,
                  "strings": ["one", "two"],
                  "numbers": [1, 2.5, -3],
                  "booleans": [true, false],
                  "children": [{"id":"first"}, {"id":"second"}],
                  "nested": {"present": false}
                }
                """);

        assertThat(struct.type()).isEqualTo(Hson.Type.STRUCT);
        assertThat(struct.asStruct()).isSameAs(struct);
        assertThat(struct.keys()).containsExactly("name", "enabled", "count", "ratio", "nothing",
                "strings", "numbers", "booleans", "children", "nested");
        assertThat(struct.stringValue("name")).contains("helidon");
        assertThat(struct.booleanValue("enabled")).contains(true);
        assertThat(struct.intValue("count")).contains(7);
        assertThat(struct.doubleValue("ratio")).contains(2.5D);
        assertThat(struct.numberValue("ratio")).contains(new BigDecimal("2.5"));
        assertThat(struct.value("nothing")).hasValueSatisfying(value -> {
            assertThat(value.type()).isEqualTo(Hson.Type.NULL);
            assertThat(value.value()).isNull();
        });
        assertThat(struct.stringValue("nothing")).isEmpty();
        assertThat(struct.stringArray("strings")).contains(List.of("one", "two"));
        assertThat(struct.numberArray("numbers")).contains(List.of(
                new BigDecimal("1"), new BigDecimal("2.5"), new BigDecimal("-3")));
        assertThat(struct.booleanArray("booleans")).contains(List.of(true, false));
        assertThat(struct.structArray("children")).hasValueSatisfying(children -> assertThat(children)
                .extracting(child -> child.stringValue("id").orElseThrow())
                .containsExactly("first", "second"));
        assertThat(struct.structValue("nested")).hasValueSatisfying(nested -> assertThat(nested.booleanValue("present"))
                .contains(false));
    }

    @Test
    void parseScientificNotationNumbers() {
        Hson.Struct struct = parseStruct("""
                {
                  "small": 1.25e-3,
                  "large": -6.02E+23,
                  "values": [3e2, -4.5E-1]
                }
                """);

        assertThat(struct.numberValue("small")).contains(new BigDecimal("1.25e-3"));
        assertThat(struct.doubleValue("small")).contains(0.00125D);
        assertThat(struct.numberValue("large")).contains(new BigDecimal("-6.02E+23"));
        assertThat(struct.doubleValue("large")).contains(-6.02E23D);
        assertThat(struct.numberArray("values")).contains(List.of(new BigDecimal("3e2"), new BigDecimal("-4.5E-1")));
    }

    @Test
    void parseStringEscapesAndPrimitiveArrays() {
        String source = "{\"text\":\"quote\\\" slash\\/ backslash\\\\ line\\n tab\\t carriage\\r "
                + "backspace\\b form\\f unicode" + "\\u" + "0041\","
                + "\"empty\":[],\"nestedArray\":[[1,2],[3]]}";

        Hson.Struct struct = parseStruct(source);

        assertThat(struct.stringValue("text")).contains(
                "quote\" slash/ backslash\\ line\n tab\t carriage\r backspace\b form\f unicodeA");
        assertThat(struct.arrayValue("empty")).hasValueSatisfying(array -> {
            assertThat(array.type()).isEqualTo(Hson.Type.ARRAY);
            assertThat(array.value()).isEmpty();
            assertThat(array.asArray()).isSameAs(array);
        });
        assertThat(struct.arrayValue("nestedArray")).hasValueSatisfying(array -> {
            assertThat(array.value()).hasSize(2);
            assertThat(array.value().get(0).asArray().getNumbers())
                    .containsExactly(new BigDecimal("1"), new BigDecimal("2"));
            assertThat(array.value().get(1).asArray().getNumbers()).containsExactly(new BigDecimal("3"));
        });
    }

    @Test
    void typedArrayAccessorsIgnoreNullElements() {
        Hson.Struct struct = parseStruct("""
                {
                  "strings": ["first", null, "second"],
                  "numbers": [null, 1, 2.5],
                  "booleans": [true, null, false],
                  "structs": [{"id":"alpha"}, null, {"id":"beta"}]
                }
                """);

        assertThat(struct.stringArray("strings")).contains(List.of("first", "second"));
        assertThat(struct.numberArray("numbers")).contains(List.of(new BigDecimal("1"), new BigDecimal("2.5")));
        assertThat(struct.booleanArray("booleans")).contains(List.of(true, false));
        assertThat(struct.structArray("structs")).hasValueSatisfying(structs -> assertThat(structs)
                .extracting(item -> item.stringValue("id").orElseThrow())
                .containsExactly("alpha", "beta"));

        Hson.Array strings = struct.arrayValue("strings").orElseThrow();
        assertThat(strings.value()).hasSize(3);
        assertThat(strings.value().get(1).type()).isEqualTo(Hson.Type.NULL);
        assertThat(strings.getStrings()).containsExactly("first", "second");

        Hson.Array structs = struct.arrayValue("structs").orElseThrow();
        assertThat(structs.value()).hasSize(3);
        assertThat(structs.value().get(1).value()).isNull();
        assertThat(structs.getStructs())
                .extracting(item -> item.stringValue("id").orElseThrow())
                .containsExactly("alpha", "beta");
    }

    @Test
    void buildStructsWithAllScalarAndArraySetters() {
        Hson.Struct nested = Hson.Struct.builder()
                .set("child", "value")
                .build();
        Hson.Array explicitArray = Hson.Array.create(List.of(nested, Hson.Struct.create()));

        Hson.Struct struct = Hson.structBuilder()
                .set("string", "metadata")
                .set("boolean", true)
                .set("double", 10.25D)
                .set("float", 1.5F)
                .set("int", 42)
                .set("long", 1234567890123L)
                .set("decimal", new BigDecimal("123.450"))
                .setNull("null")
                .set("explicitArray", explicitArray)
                .setStructs("structs", List.of(nested))
                .setStrings("strings", List.of("alpha", "beta"))
                .setLongs("longs", List.of(5L, 6L))
                .setDoubles("doubles", List.of(2.25D, 3.5D))
                .setNumbers("numbers", List.of(new BigDecimal("7.75"), new BigDecimal("8")))
                .setBooleans("booleans", List.of(true, false))
                .set("removed", "before")
                .unset("removed")
                .build();

        assertThat(struct.stringValue("string", "fallback")).isEqualTo("metadata");
        assertThat(struct.booleanValue("boolean", false)).isTrue();
        assertThat(struct.doubleValue("double", 0D)).isEqualTo(10.25D);
        assertThat(struct.numberValue("float")).contains(new BigDecimal("1.5"));
        assertThat(struct.intValue("int", 0)).isEqualTo(42);
        assertThat(struct.numberValue("long")).contains(new BigDecimal("1234567890123"));
        assertThat(struct.numberValue("decimal", BigDecimal.ZERO)).isEqualByComparingTo("123.450");
        assertThat(struct.value("null"))
                .hasValueSatisfying(value -> assertThat(value.type()).isEqualTo(Hson.Type.NULL));
        assertThat(struct.value("removed")).isEmpty();
        assertThat(struct.stringValue("missing", "fallback")).isEqualTo("fallback");
        assertThat(struct.booleanValue("missing", true)).isTrue();
        assertThat(struct.intValue("missing", 11)).isEqualTo(11);
        assertThat(struct.doubleValue("missing", 12.5D)).isEqualTo(12.5D);
        assertThat(struct.numberValue("missing", BigDecimal.TEN)).isEqualByComparingTo(BigDecimal.TEN);

        assertThat(struct.arrayValue("explicitArray"))
                .hasValueSatisfying(array -> assertThat(array).isSameAs(explicitArray));
        assertThat(struct.structArray("structs")).contains(List.of(nested));
        assertThat(struct.stringArray("strings")).contains(List.of("alpha", "beta"));
        assertThat(struct.numberArray("longs")).contains(List.of(new BigDecimal("5"), new BigDecimal("6")));
        assertThat(struct.numberArray("doubles")).contains(List.of(new BigDecimal("2.25"), new BigDecimal("3.5")));
        assertThat(struct.numberArray("numbers")).contains(List.of(new BigDecimal("7.75"), new BigDecimal("8")));
        assertThat(struct.booleanArray("booleans")).contains(List.of(true, false));
        assertThatThrownBy(() -> struct.keys().add("new-key")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void createArraysFromFactoryMethods() {
        Hson.Array strings = Hson.Array.createStrings(List.of("first", "second"));
        Hson.Array numbers = Hson.Array.createNumbers(List.of(new BigDecimal("1.00"), new BigDecimal("2.50")));
        Hson.Array booleans = Hson.Array.createBooleans(List.of(true, false));
        Hson.Array ints = Hson.Array.create(1, 2, 3);
        Hson.Array longs = Hson.Array.create(4L, 5L);
        Hson.Array doubles = Hson.Array.create(6.25D, 7.5D);
        Hson.Array floats = Hson.Array.create(8.125F, 9.5F);

        assertThat(strings.getStrings()).containsExactly("first", "second");
        assertThat(numbers.getNumbers()).containsExactly(new BigDecimal("1.00"), new BigDecimal("2.50"));
        assertThat(booleans.getBooleans()).containsExactly(true, false);
        assertThat(ints.getNumbers()).containsExactly(new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"));
        assertThat(longs.getNumbers()).containsExactly(new BigDecimal("4"), new BigDecimal("5"));
        assertThat(doubles.getNumbers()).containsExactly(new BigDecimal("6.25"), new BigDecimal("7.5"));
        assertThat(floats.getNumbers()).containsExactly(new BigDecimal("8.125"), new BigDecimal("9.5"));
        assertThat(Hson.Array.create().value()).isEmpty();
    }

    @Test
    void writeCompactAndPrettyHsonCanBeParsedAgain() {
        Hson.Struct document = Hson.Struct.builder()
                .set("name", "helidon")
                .set("number", new BigDecimal("123.450"))
                .set("active", true)
                .setStrings("tags", List.of("metadata", "hson"))
                .setStructs("children", List.of(Hson.Struct.builder().set("id", 1).build()))
                .build();

        String compact = write(document, false);
        String pretty = write(document, true);

        assertThat(compact).isEqualTo("{\"name\":\"helidon\",\"number\":123.450,\"active\":true,"
                + "\"tags\":[\"metadata\",\"hson\"],\"children\":[{\"id\":1}]}");
        assertThat(pretty).contains("\n").contains("  ");
        assertThat(Hson.parse(input(compact))).isEqualTo(document);
        assertThat(Hson.parse(input(pretty))).isEqualTo(document);
    }

    @Test
    void reportTypeAndSyntaxErrorsThroughPublicExceptions() {
        Hson.Struct struct = parseStruct("{\"name\":\"helidon\",\"values\":[1,2]}");

        assertThatExceptionOfType(HsonException.class).isThrownBy(() -> struct.intValue("name"));
        assertThatExceptionOfType(HsonException.class).isThrownBy(() -> struct.stringArray("values"));
        assertThatExceptionOfType(HsonException.class)
                .isThrownBy(() -> struct.value("name").orElseThrow().asArray());
        assertThatExceptionOfType(HsonException.class).isThrownBy(() -> struct.arrayValue("name"));
        assertThatExceptionOfType(HsonException.class).isThrownBy(() -> Hson.parse(input("true")));
        assertThatExceptionOfType(HsonException.class).isThrownBy(() -> Hson.parse(input("{\"missingColon\" 1}")));
        assertThatExceptionOfType(HsonException.class).isThrownBy(() -> Hson.parse(input("[1, invalid]")));
        assertThat(Hson.Type.valueOf("STRING")).isEqualTo(Hson.Type.STRING);
        assertThat(Set.of(Hson.Type.values())).contains(Hson.Type.STRING, Hson.Type.NUMBER, Hson.Type.BOOLEAN,
                Hson.Type.NULL, Hson.Type.STRUCT, Hson.Type.ARRAY);
    }

    private static Hson.Struct parseStruct(String source) {
        Hson.Value<?> value = Hson.parse(input(source));
        assertThat(value.type()).isEqualTo(Hson.Type.STRUCT);
        return value.asStruct();
    }

    private static ByteArrayInputStream input(String source) {
        return new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
    }

    private static String write(Hson.Value<?> value, boolean pretty) {
        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            value.write(printWriter, pretty);
        }
        return writer.toString();
    }
}
