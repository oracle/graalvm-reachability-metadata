/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_vaadin_external_google.android_json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;
import org.junit.jupiter.api.Test;

public class Android_jsonTest {
    @Test
    void parsesLenientDocumentsAndProvidesTypedAccessors() throws JSONException {
        final String input = "\uFEFF{\n"
                + "  # comment\n"
                + "  unquoted: 0x10,\n"
                + "  octal: 010,\n"
                + "  decimal: 3.5,\n"
                + "  truthy: true,\n"
                + "  nullable: null,\n"
                + "  alias => 'value',\n"
                + "  array: [1,,2;3,],\n"
                + "  nested: { name: 'android-json' }\n"
                + "}";

        final JSONObject object = new JSONObject(input);
        final JSONArray array = object.getJSONArray("array");
        final JSONArray selectedValues = object.toJSONArray(new JSONArray(List.of("alias", "missing", "nested")));

        assertThat(object.length()).isEqualTo(8);
        assertThat(object.getInt("unquoted")).isEqualTo(16);
        assertThat(object.getInt("octal")).isEqualTo(8);
        assertThat(object.getDouble("decimal")).isEqualTo(3.5d);
        assertThat(object.getBoolean("truthy")).isTrue();
        assertThat(object.isNull("nullable")).isTrue();
        assertThat(object.get("nullable")).isSameAs(JSONObject.NULL);
        assertThat(object.getString("alias")).isEqualTo("value");
        assertThat(object.getJSONObject("nested").getString("name")).isEqualTo("android-json");

        assertThat(array.length()).isEqualTo(5);
        assertThat(array.getInt(0)).isEqualTo(1);
        assertThat(array.isNull(1)).isTrue();
        assertThat(array.getInt(2)).isEqualTo(2);
        assertThat(array.getInt(3)).isEqualTo(3);
        assertThat(array.isNull(4)).isTrue();

        assertThat(selectedValues.length()).isEqualTo(3);
        assertThat(selectedValues.getString(0)).isEqualTo("value");
        assertThat(selectedValues.isNull(1)).isTrue();
        assertThat(selectedValues.getJSONObject(2).getString("name")).isEqualTo("android-json");

        assertThat(toStringSet(object.names())).containsExactlyInAnyOrder(
                "unquoted",
                "octal",
                "decimal",
                "truthy",
                "nullable",
                "alias",
                "array",
                "nested");
        assertThat(toStringSet(object.keys())).containsExactlyInAnyOrder(
                "unquoted",
                "octal",
                "decimal",
                "truthy",
                "nullable",
                "alias",
                "array",
                "nested");
        assertThat(object.optBoolean("missing", true)).isTrue();
        assertThat(object.optString("missing", "fallback")).isEqualTo("fallback");
        assertThat(object.optJSONArray("missing")).isNull();
        assertThat(object.optJSONObject("missing")).isNull();
    }

    @Test
    void mutatesObjectsWrapsJavaTypesAndEncodesValues() throws JSONException {
        final JSONObject object = new JSONObject(Map.of(
                "numbers", List.of(1, 2, 3),
                "letters", new String[] {"a", "b"},
                "uri", URI.create("https://example.com/items")));

        object.put("active", true);
        object.put("negativeZero", -0.0d);
        object.put("nullable", JSONObject.NULL);
        object.putOpt("ignored", null);
        object.accumulate("history", "created");
        object.accumulate("history", 2);
        object.put("temporary", "remove-me");

        final JSONObject copy = new JSONObject(object, new String[] {"uri", "negativeZero", "missing"});
        final String quoted = JSONObject.quote("a/b\"\\\n\t");
        final String prettyPrinted = object.toString(2);

        assertThat(object.getJSONArray("numbers").getInt(2)).isEqualTo(3);
        assertThat(object.getJSONArray("letters").getString(1)).isEqualTo("b");
        assertThat(object.getString("uri")).isEqualTo("https://example.com/items");
        assertThat(object.getBoolean("active")).isTrue();
        assertThat(object.getJSONArray("history").getString(0)).isEqualTo("created");
        assertThat(object.getJSONArray("history").getInt(1)).isEqualTo(2);
        assertThat(object.remove("temporary")).isEqualTo("remove-me");
        assertThat(object.remove("missing")).isNull();
        assertThat(object.has("ignored")).isFalse();
        assertThat(object.isNull("nullable")).isTrue();

        assertThat(copy.length()).isEqualTo(2);
        assertThat(copy.getString("uri")).isEqualTo("https://example.com/items");
        assertThat(JSONObject.numberToString(-0.0d)).isEqualTo("-0");
        assertThat(JSONObject.numberToString(42)).isEqualTo("42");
        assertThat(quoted)
                .startsWith("\"")
                .endsWith("\"")
                .contains("\\/")
                .contains("\\\"")
                .contains("\\\\")
                .contains("\\n")
                .contains("\\t");
        assertThat(prettyPrinted).contains("\n  \"").contains("\"negativeZero\": -0");

        assertThat(JSONObject.wrap(List.of("x", "y"))).isInstanceOf(JSONArray.class);
        assertThat(JSONObject.wrap(Map.of("answer", 42))).isInstanceOf(JSONObject.class);
        assertThat(JSONObject.wrap(new CustomValue())).isNull();
        assertThat(JSONObject.wrap(null)).isSameAs(JSONObject.NULL);
        assertThatThrownBy(() -> object.put("bad", Double.NaN))
                .isInstanceOf(JSONException.class)
                .hasMessageContaining("Forbidden numeric value");
    }

    @Test
    void convertsArraysAndBuildsJsonText() throws JSONException {
        final JSONArray values = new JSONArray(new int[] {1, 2, 3});
        values.put(3, new JSONObject(Map.of("name", "android-json")));
        values.put(4, 5L);
        values.put(5, new JSONArray(List.of("x", "y")));

        final JSONObject mapped = values.toJSONObject(new JSONArray(List.of(
                "first",
                "second",
                "third",
                "details",
                "count",
                "letters",
                "ignored")));
        final JSONStringer stringer = new JSONStringer()
                .object()
                .key("name").value("android-json")
                .key("values").array().value(true).value(7).endArray()
                .endObject();

        assertThat(values.length()).isEqualTo(6);
        assertThat(values.getInt(0)).isEqualTo(1);
        assertThat(values.getInt(1)).isEqualTo(2);
        assertThat(values.getInt(2)).isEqualTo(3);
        assertThat(values.getJSONObject(3).getString("name")).isEqualTo("android-json");
        assertThat(values.getLong(4)).isEqualTo(5L);
        assertThat(values.getJSONArray(5).join("|")).isEqualTo("\"x\"|\"y\"");

        assertThat(mapped.getInt("first")).isEqualTo(1);
        assertThat(mapped.getInt("second")).isEqualTo(2);
        assertThat(mapped.getInt("third")).isEqualTo(3);
        assertThat(mapped.getJSONObject("details").getString("name")).isEqualTo("android-json");
        assertThat(mapped.getLong("count")).isEqualTo(5L);
        assertThat(mapped.getJSONArray("letters").getString(0)).isEqualTo("x");
        assertThat(mapped.has("ignored")).isFalse();

        final Object removed = values.remove(1);
        assertThat(removed).isEqualTo(2);
        assertThat(values.length()).isEqualTo(5);
        assertThat(values.getInt(1)).isEqualTo(3);
        assertThat(values.optInt(99, -1)).isEqualTo(-1);
        assertThat(stringer.toString()).isEqualTo("{\"name\":\"android-json\",\"values\":[true,7]}");
        assertThatThrownBy(() -> new JSONStringer().value("orphan"))
                .isInstanceOf(JSONException.class)
                .hasMessageContaining("Nesting problem");
    }

    @Test
    void buildsArraysFromCollectionsAndComparesNestedArrayContent() throws JSONException {
        final List<Object> sourceValues = new ArrayList<>();
        sourceValues.add("alpha");
        sourceValues.add(null);
        sourceValues.add(List.of(1, 2, 3));
        sourceValues.add(List.of(true, false));

        final JSONArray fromCollection = new JSONArray(sourceValues);
        final JSONArray equivalent = new JSONArray("[\"alpha\",null,[1,2,3],[true,false]]");
        final JSONArray different = new JSONArray("[\"alpha\",null,[1,2],[true,false]]");
        final String prettyPrinted = fromCollection.toString(2);

        assertThat(fromCollection.length()).isEqualTo(4);
        assertThat(fromCollection.getString(0)).isEqualTo("alpha");
        assertThat(fromCollection.isNull(1)).isTrue();
        assertThat(fromCollection.getJSONArray(2).getInt(2)).isEqualTo(3);
        assertThat(fromCollection.getJSONArray(3).getBoolean(0)).isTrue();
        assertThat(fromCollection.getJSONArray(3).getBoolean(1)).isFalse();

        assertThat(fromCollection).isEqualTo(equivalent);
        assertThat(fromCollection.hashCode()).isEqualTo(equivalent.hashCode());
        assertThat(fromCollection).isNotEqualTo(different);
        assertThat(prettyPrinted)
                .contains("\n  null,")
                .contains("\n  [\n    1,")
                .contains("\n  [\n    true,");
    }

    @Test
    void advancesTokenerCursorAndReportsSyntaxContext() throws JSONException {
        final JSONTokener tokener = new JSONTokener(" abcXYZdef");
        final JSONTokener escapedStringTokener = new JSONTokener("/*comment*/ \"A\\u0042\\n\"");

        assertThat(tokener.more()).isTrue();
        assertThat(tokener.next()).isEqualTo(' ');
        tokener.back();
        assertThat(tokener.next(' ')).isEqualTo(' ');
        assertThat(tokener.next(3)).isEqualTo("abc");
        assertThat(tokener.nextTo('Z')).isEqualTo("XY");
        assertThat(tokener.skipTo('d')).isEqualTo('d');
        assertThat(tokener.next('d')).isEqualTo('d');
        tokener.skipPast("ef");
        assertThat(tokener.more()).isFalse();

        assertThat(escapedStringTokener.nextClean()).isEqualTo('"');
        assertThat(escapedStringTokener.nextString('"')).isEqualTo("AB\n");
        assertThat(JSONTokener.dehexchar('A')).isEqualTo(10);
        assertThat(JSONTokener.dehexchar('f')).isEqualTo(15);
        assertThat(JSONTokener.dehexchar('x')).isEqualTo(-1);
        assertThat(new JSONTokener("oops").syntaxError("Bad token").getMessage())
                .isEqualTo("Bad token at character 0 of oops");
    }

    private static Set<String> toStringSet(final JSONArray jsonArray) throws JSONException {
        final Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < jsonArray.length(); index++) {
            values.add(jsonArray.getString(index));
        }
        return values;
    }

    private static Set<String> toStringSet(final Iterator<?> iterator) {
        final Set<String> values = new LinkedHashSet<>();
        while (iterator.hasNext()) {
            values.add((String) iterator.next());
        }
        return values;
    }

    private static final class CustomValue {
    }
}
