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

import io.helidon.metadata.hson.Hson;
import io.helidon.metadata.hson.HsonException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Helidon_metadata_hsonTest {
    @Test
    void parsesNestedStructsArraysAndPrimitiveValues() {
        String document = """
                {
                  "name": "helidon\\nmetadata",
                  "enabled": true,
                  "retry": 3,
                  "ratio": 1.25,
                  "missing": null,
                  "labels": ["hson", "native", null],
                  "numbers": [1, 2.5, null, -4],
                  "flags": [true, null, false],
                  "child": {"id": 7, "value": "nested"},
                  "children": [{"id": 1}, {"id": 2}],
                  "mixed": ["text", 9, false, {"kind":"struct"}, [1, 2]]
                }
                """;

        Hson.Struct root = parse(document).asStruct();

        assertThat(root.type()).isEqualTo(Hson.Type.STRUCT);
        assertThat(root.keys()).containsExactly("name", "enabled", "retry", "ratio", "missing", "labels",
                "numbers", "flags", "child", "children", "mixed");
        assertThat(root.stringValue("name")).hasValue("helidon\nmetadata");
        assertThat(root.booleanValue("enabled")).hasValue(true);
        assertThat(root.intValue("retry")).hasValue(3);
        assertThat(root.doubleValue("ratio")).hasValue(1.25D);
        assertThat(root.numberValue("ratio").orElseThrow()).isEqualByComparingTo("1.25");
        assertThat(root.stringValue("missing")).isEmpty();
        assertThat(root.stringValue("absent", "fallback")).isEqualTo("fallback");
        assertThat(root.booleanValue("absent", false)).isFalse();
        assertThat(root.intValue("absent", 42)).isEqualTo(42);
        assertThat(root.doubleValue("absent", 2.5D)).isEqualTo(2.5D);
        assertThat(root.numberValue("absent", BigDecimal.TEN)).isEqualByComparingTo(BigDecimal.TEN);

        assertThat(root.stringArray("labels").orElseThrow()).containsExactly("hson", "native");
        assertThat(root.numberArray("numbers").orElseThrow())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("1"), new BigDecimal("2.5"), new BigDecimal("-4"));
        assertThat(root.booleanArray("flags").orElseThrow()).containsExactly(true, false);

        Hson.Struct child = root.structValue("child").orElseThrow();
        assertThat(child.intValue("id")).hasValue(7);
        assertThat(child.stringValue("value")).hasValue("nested");
        assertThat(root.structArray("children").orElseThrow())
                .extracting(item -> item.intValue("id").orElseThrow())
                .containsExactly(1, 2);

        Hson.Array mixed = root.arrayValue("mixed").orElseThrow();
        assertThat(mixed.type()).isEqualTo(Hson.Type.ARRAY);
        assertThat(mixed.value()).extracting(Hson.Value::type)
                .containsExactly(Hson.Type.STRING, Hson.Type.NUMBER, Hson.Type.BOOLEAN,
                        Hson.Type.STRUCT, Hson.Type.ARRAY);
        assertThat(mixed.value().get(3).asStruct().stringValue("kind")).hasValue("struct");
        assertThat(mixed.value().get(4).asArray().getNumbers())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("1"), new BigDecimal("2"));
    }

    @Test
    void builderCreatesTypedValuesAndSupportsUnsetAndNull() {
        Hson.Struct firstChild = Hson.Struct.builder()
                .set("name", "first")
                .set("index", 1)
                .build();
        Hson.Struct secondChild = Hson.Struct.builder()
                .set("name", "second")
                .set("index", 2L)
                .build();

        Hson.Struct struct = Hson.structBuilder()
                .set("string", "value")
                .set("boolean", true)
                .set("int", 11)
                .set("long", 12L)
                .set("double", 13.5D)
                .set("float", 14.25F)
                .set("decimal", new BigDecimal("15.75"))
                .setStrings("strings", List.of("alpha", "beta"))
                .setLongs("longs", List.of(21L, 22L))
                .setDoubles("doubles", List.of(1.5D, 2.5D))
                .setNumbers("numbers", List.of(new BigDecimal("3.5"), new BigDecimal("4.5")))
                .setBooleans("booleans", List.of(true, false))
                .setStructs("children", List.of(firstChild, secondChild))
                .set("emptyArray", Hson.Array.create())
                .setNull("nullValue")
                .set("removed", "temporary")
                .unset("removed")
                .build();

        assertThat(struct.keys()).doesNotContain("removed");
        assertThat(struct.stringValue("string")).hasValue("value");
        assertThat(struct.booleanValue("boolean")).hasValue(true);
        assertThat(struct.intValue("int")).hasValue(11);
        assertThat(struct.numberValue("long").orElseThrow()).isEqualByComparingTo("12");
        assertThat(struct.doubleValue("double")).hasValue(13.5D);
        assertThat(struct.numberValue("float").orElseThrow()).isEqualByComparingTo("14.25");
        assertThat(struct.numberValue("decimal").orElseThrow()).isEqualByComparingTo("15.75");
        assertThat(struct.stringArray("strings")).hasValue(List.of("alpha", "beta"));
        assertThat(struct.numberArray("longs").orElseThrow())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("21"), new BigDecimal("22"));
        assertThat(struct.numberArray("doubles").orElseThrow())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("1.5"), new BigDecimal("2.5"));
        assertThat(struct.numberArray("numbers").orElseThrow())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("3.5"), new BigDecimal("4.5"));
        assertThat(struct.booleanArray("booleans")).hasValue(List.of(true, false));
        assertThat(struct.structArray("children").orElseThrow())
                .extracting(child -> child.stringValue("name").orElseThrow())
                .containsExactly("first", "second");
        assertThat(struct.arrayValue("emptyArray").orElseThrow().value()).isEmpty();
        assertThat(struct.value("nullValue").orElseThrow().type()).isEqualTo(Hson.Type.NULL);
        assertThat(struct.stringValue("nullValue")).isEmpty();
    }

    @Test
    void arrayFactoriesCreateTypedImmutableArrays() {
        Hson.Array strings = Hson.Array.createStrings(List.of("one", "two"));
        Hson.Array booleans = Hson.Array.createBooleans(List.of(true, false));
        Hson.Array numbers = Hson.Array.createNumbers(List.of(new BigDecimal("1.25"), new BigDecimal("2.50")));
        Hson.Array ints = Hson.Array.create(1, 2, 3);
        Hson.Array longs = Hson.Array.create(4L, 5L);
        Hson.Array doubles = Hson.Array.create(6.5D, 7.5D);
        Hson.Array floats = Hson.Array.create(8.25F, 9.5F);

        assertThat(strings.getStrings()).containsExactly("one", "two");
        assertThat(booleans.getBooleans()).containsExactly(true, false);
        assertThat(numbers.getNumbers())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("1.25"), new BigDecimal("2.50"));
        assertThat(ints.getNumbers())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"));
        assertThat(longs.getNumbers())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("4"), new BigDecimal("5"));
        assertThat(doubles.getNumbers())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("6.5"), new BigDecimal("7.5"));
        assertThat(floats.getNumbers())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("8.25"), new BigDecimal("9.5"));
        assertThatThrownBy(() -> strings.value().add(parse("{}").asStruct()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void arrayFactoryCombinesExistingValuesIntoDocumentArrays() {
        Hson.Struct source = Hson.Struct.builder()
                .set("name", "alpha")
                .set("count", 2)
                .set("empty", Hson.Struct.create())
                .setNull("missing")
                .build();
        List<Hson.Value<?>> values = List.of(source.value("name").orElseThrow(),
                source.value("count").orElseThrow(),
                source.value("empty").orElseThrow(),
                source.value("missing").orElseThrow());

        Hson.Array array = Hson.Array.create(values);
        Hson.Struct document = Hson.Struct.builder()
                .set("values", array)
                .build();

        assertThat(array.value()).extracting(Hson.Value::type)
                .containsExactly(Hson.Type.STRING, Hson.Type.NUMBER, Hson.Type.STRUCT, Hson.Type.NULL);
        assertThat(array.value().get(0).value()).isEqualTo("alpha");
        assertThat(array.value().get(1).value()).isEqualTo(new BigDecimal("2"));
        assertThat(array.value().get(2).asStruct().keys()).isEmpty();
        assertThat(array.value().get(3).value()).isNull();
        assertThat(write(document, false)).isEqualTo("{\"values\":[\"alpha\",2,{},null]}");
        assertThat(parse(write(document, false))).isEqualTo(document);
    }

    @Test
    void writesCompactAndPrettyDocumentsThatParseBackToEquivalentValues() {
        Hson.Struct document = Hson.Struct.builder()
                .set("text", "line1\nline2\t\"quoted\"")
                .set("active", false)
                .set("scores", Hson.Array.create(10, 20))
                .setStructs("items", List.of(Hson.Struct.builder().set("id", 1).build()))
                .build();

        String compact = write(document, false);
        String pretty = write(document, true);

        assertThat(compact).isEqualTo("{\"text\":\"line1\\nline2\\t\\\"quoted\\\"\",\"active\":false,"
                + "\"scores\":[10,20],\"items\":[{\"id\":1}]}");
        assertThat(pretty).contains("\n").contains("\"scores\": [").contains("\"items\": [");
        assertThat(parse(compact)).isEqualTo(document);
        assertThat(parse(pretty)).isEqualTo(document);
    }

    @Test
    void typedAccessReportsMismatchesAndInvalidDocuments() {
        Hson.Struct struct = parse("{\"name\":\"helidon\",\"values\":[1,\"two\"],\"array\":[]}").asStruct();

        assertThatThrownBy(() -> struct.intValue("name"))
                .isInstanceOf(HsonException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> struct.stringArray("values"))
                .isInstanceOf(HsonException.class);
        assertThatThrownBy(() -> struct.value("name").orElseThrow().asStruct())
                .isInstanceOf(HsonException.class);
        assertThatThrownBy(() -> struct.value("name").orElseThrow().asArray())
                .isInstanceOf(HsonException.class);
        assertThatThrownBy(() -> parse("{\"broken\": not-a-number}"))
                .isInstanceOf(HsonException.class);
        assertThatThrownBy(() -> parse("\"top-level-scalar\""))
                .isInstanceOf(HsonException.class);
    }

    private static Hson.Value<?> parse(String document) {
        byte[] bytes = document.getBytes(StandardCharsets.UTF_8);
        return Hson.parse(new ByteArrayInputStream(bytes));
    }

    private static String write(Hson.Value<?> value, boolean pretty) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        value.write(printWriter, pretty);
        printWriter.flush();
        return stringWriter.toString();
    }
}
