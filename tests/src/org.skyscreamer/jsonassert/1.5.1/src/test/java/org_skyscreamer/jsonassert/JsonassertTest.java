/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_skyscreamer.jsonassert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.ArrayValueMatcher;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompare;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;
import org.skyscreamer.jsonassert.JSONParser;
import org.skyscreamer.jsonassert.LocationAwareValueMatcher;
import org.skyscreamer.jsonassert.RegularExpressionValueMatcher;
import org.skyscreamer.jsonassert.ValueMatcherException;
import org.skyscreamer.jsonassert.comparator.ArraySizeComparator;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.skyscreamer.jsonassert.comparator.DefaultComparator;
import org.skyscreamer.jsonassert.comparator.JSONCompareUtil;
import org.skyscreamer.jsonassert.comparator.JSONComparator;

public class JsonassertTest {

    private static final String CATALOG_EXPECTED = """
            {
              "catalog": {
                "owner": "test-team",
                "items": [
                  {"id": 1, "name": "alpha", "labels": ["fast", "json"]},
                  {"id": 2, "name": "beta", "labels": ["native", "test"]}
                ]
              }
            }
            """;

    private static final String CATALOG_ACTUAL_WITH_EXTRAS_AND_REORDERING = """
            {
              "catalog": {
                "items": [
                  {"name": "beta", "id": 2, "labels": ["test", "native"], "score": 20},
                  {"name": "alpha", "id": 1, "labels": ["json", "fast"], "score": 10}
                ],
                "owner": "test-team",
                "generatedBy": "integration-test"
              },
              "requestId": "req-001"
            }
            """;

    @Test
    public void compareModesExposeExtensibilityAndOrderingFlags() {
        assertThat(JSONCompareMode.STRICT.isExtensible()).isFalse();
        assertThat(JSONCompareMode.STRICT.hasStrictOrder()).isTrue();
        assertThat(JSONCompareMode.LENIENT.isExtensible()).isTrue();
        assertThat(JSONCompareMode.LENIENT.hasStrictOrder()).isFalse();
        assertThat(JSONCompareMode.NON_EXTENSIBLE.isExtensible()).isFalse();
        assertThat(JSONCompareMode.NON_EXTENSIBLE.hasStrictOrder()).isFalse();
        assertThat(JSONCompareMode.STRICT_ORDER.isExtensible()).isTrue();
        assertThat(JSONCompareMode.STRICT_ORDER.hasStrictOrder()).isTrue();

        assertThat(JSONCompareMode.LENIENT.withStrictOrdering(true)).isSameAs(JSONCompareMode.STRICT_ORDER);
        assertThat(JSONCompareMode.STRICT.withStrictOrdering(false)).isSameAs(JSONCompareMode.NON_EXTENSIBLE);
        assertThat(JSONCompareMode.STRICT.withExtensible(true)).isSameAs(JSONCompareMode.STRICT_ORDER);
        assertThat(JSONCompareMode.STRICT_ORDER.withExtensible(false)).isSameAs(JSONCompareMode.STRICT);
    }

    @Test
    public void lenientComparisonAllowsAdditionalFieldsAndUnorderedArrays() throws JSONException {
        JSONAssert.assertEquals(CATALOG_EXPECTED, CATALOG_ACTUAL_WITH_EXTRAS_AND_REORDERING,
                JSONCompareMode.LENIENT);
        JSONAssert.assertEquals(CATALOG_EXPECTED, CATALOG_ACTUAL_WITH_EXTRAS_AND_REORDERING, false);
        JSONAssert.assertNotEquals(CATALOG_EXPECTED, CATALOG_ACTUAL_WITH_EXTRAS_AND_REORDERING,
                JSONCompareMode.STRICT);
    }

    @Test
    public void nonExtensibleComparisonAllowsReorderingButReportsUnexpectedFields() throws JSONException {
        String expected = "{"
                + "\"items\":[{\"id\":1,\"name\":\"alpha\"},{\"id\":2,\"name\":\"beta\"}],"
                + "\"count\":2} ";
        String actualWithSameFields = "{"
                + "\"count\":2,"
                + "\"items\":[{\"name\":\"beta\",\"id\":2},{\"name\":\"alpha\",\"id\":1}]}";
        String actualWithUnexpectedField = "{"
                + "\"count\":2,\"source\":\"cache\","
                + "\"items\":[{\"name\":\"beta\",\"id\":2},{\"name\":\"alpha\",\"id\":1}]}";

        assertThat(JSONCompare.compareJSON(expected, actualWithSameFields, JSONCompareMode.NON_EXTENSIBLE).passed())
                .isTrue();

        JSONCompareResult result = JSONCompare.compareJSON(expected, actualWithUnexpectedField,
                JSONCompareMode.NON_EXTENSIBLE);

        assertThat(result.failed()).isTrue();
        assertThat(result.isUnexpectedOnField()).isTrue();
        assertThat(result.getFieldUnexpected())
                .anySatisfy(failure -> {
                    assertThat(failure.getField()).isEqualTo("");
                    assertThat(failure.getActual()).isEqualTo("source");
                });
    }

    @Test
    public void strictOrderComparisonRequiresArraySequenceButStillAllowsExtraObjectFields() throws JSONException {
        String expected = "{"
                + "\"friends\":[{\"id\":2,\"name\":\"Sam\"},{\"id\":3,\"name\":\"Riley\"}]}";
        String actualWithExtraField = "{"
                + "\"friends\":[{\"id\":2,\"name\":\"Sam\"},{\"id\":3,\"name\":\"Riley\"}],"
                + "\"page\":1}";
        String actualWithReorderedArray = "{"
                + "\"friends\":[{\"id\":3,\"name\":\"Riley\"},{\"id\":2,\"name\":\"Sam\"}],"
                + "\"page\":1}";

        JSONAssert.assertEquals(expected, actualWithExtraField, JSONCompareMode.STRICT_ORDER);
        JSONAssert.assertNotEquals(expected, actualWithReorderedArray, JSONCompareMode.STRICT_ORDER);

        JSONCompareResult result = JSONCompare.compareJSON(expected, actualWithReorderedArray,
                JSONCompareMode.STRICT_ORDER);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFieldFailures())
                .extracting(failure -> failure.getField())
                .contains("friends[0].id", "friends[0].name", "friends[1].id", "friends[1].name");
    }

    @Test
    public void objectAndArrayAssertionOverloadsCompareParsedInstances() throws JSONException {
        JSONObject actualObject = new JSONObject("{"
                + "\"id\":7,\"active\":true,\"roles\":[\"writer\",\"reader\"],\"extra\":\"ignored\"}");
        JSONArray actualArray = new JSONArray("["
                + "{\"code\":\"B\",\"enabled\":false},{\"code\":\"A\",\"enabled\":true}]");

        JSONAssert.assertEquals("{\"active\":true,\"id\":7,\"roles\":[\"reader\",\"writer\"]}",
                actualObject, JSONCompareMode.LENIENT);
        JSONAssert.assertEquals(new JSONObject("{\"id\":7,\"active\":true}"), actualObject, false);
        JSONAssert.assertNotEquals("{\"id\":8}", actualObject, false);

        JSONAssert.assertEquals("[{\"enabled\":true,\"code\":\"A\"},{\"enabled\":false,\"code\":\"B\"}]",
                actualArray, JSONCompareMode.LENIENT);
        JSONAssert.assertEquals(new JSONArray("[{\"code\":\"B\"},{\"code\":\"A\"}]"), actualArray, false);
        JSONAssert.assertNotEquals(new JSONArray("[{\"code\":\"A\"},{\"code\":\"B\"}]"), actualArray,
                JSONCompareMode.STRICT_ORDER);
    }

    @Test
    public void compareResultReportsFieldFailuresMissingFieldsAndUnexpectedFields() throws JSONException {
        String expected = "{\"id\":7,\"name\":\"jsonassert\",\"enabled\":true}";
        String actual = "{\"id\":8,\"extra\":1}";

        JSONCompareResult result = JSONCompare.compareJSON(expected, actual, JSONCompareMode.NON_EXTENSIBLE);

        assertThat(result.failed()).isTrue();
        assertThat(result.passed()).isFalse();
        assertThat(result.isFailureOnField()).isTrue();
        assertThat(result.isMissingOnField()).isTrue();
        assertThat(result.isUnexpectedOnField()).isTrue();
        assertThat(result.getFieldFailures())
                .anySatisfy(failure -> {
                    assertThat(failure.getField()).isEqualTo("id");
                    assertThat(failure.getExpected()).isEqualTo(7);
                    assertThat(failure.getActual()).isEqualTo(8);
                });
        assertThat(result.getFieldMissing())
                .extracting(failure -> failure.getExpected())
                .contains("name", "enabled");
        assertThat(result.getFieldUnexpected())
                .extracting(failure -> failure.getActual())
                .contains("extra");
        assertThat(result.getMessage()).contains("id").contains("name").contains("extra");
    }

    @Test
    public void customComparatorSupportsRegexMatchingAndWildcardPaths() throws JSONException {
        String expected = """
                {
                  "users": [
                    {"id": "ID", "profile": {"email": "EMAIL", "generatedAt": "ignored"}},
                    {"id": "ID", "profile": {"email": "EMAIL", "generatedAt": "ignored"}}
                  ]
                }
                """;
        String actual = """
                {
                  "users": [
                    {"id": "101", "profile": {"email": "alpha@example.test", "generatedAt": "2026-04-27T09:00:00Z"}},
                    {"id": "102", "profile": {"email": "beta@example.test", "generatedAt": "2026-04-27T09:01:00Z"}}
                  ]
                }
                """;
        CustomComparator comparator = new CustomComparator(JSONCompareMode.STRICT,
                Customization.customization("users[*].id", new RegularExpressionValueMatcher<>("\\d+")),
                Customization.customization("users[*].profile.email",
                        new RegularExpressionValueMatcher<>("[a-z]+@example\\.test")),
                Customization.customization("**.generatedAt",
                        (actualValue, expectedValue) -> actualValue.toString().startsWith("2026-")));

        JSONAssert.assertEquals(expected, actual, comparator);
        assertThat(JSONCompare.compareJSON(expected, actual, comparator).passed()).isTrue();
    }

    @Test
    public void locationAwareValueMatcherCanUseJsonPathWhenComparingValues() throws JSONException {
        String expected = """
                {
                  "events": [
                    {"status": "accepted", "trackingCode": "tracking"},
                    {"status": "accepted", "trackingCode": "tracking"}
                  ]
                }
                """;
        String actual = """
                {
                  "events": [
                    {"status": "accepted", "trackingCode": "tracking-0-alpha"},
                    {"status": "accepted", "trackingCode": "tracking-1-beta"}
                  ]
                }
                """;
        String actualWithWrongPathSpecificCode = """
                {
                  "events": [
                    {"status": "accepted", "trackingCode": "tracking-0-alpha"},
                    {"status": "accepted", "trackingCode": "tracking-0-beta"}
                  ]
                }
                """;
        LocationAwareValueMatcher<Object> pathAwareTrackingCodeMatcher = new LocationAwareValueMatcher<>() {
            @Override
            public boolean equal(Object actualValue, Object expectedValue) {
                return actualValue.toString().startsWith(expectedValue.toString());
            }

            @Override
            public boolean equal(String prefix, Object actualValue, Object expectedValue, JSONCompareResult result) {
                String eventIndex = prefix.substring(prefix.indexOf('[') + 1, prefix.indexOf(']'));
                String expectedPrefix = expectedValue + "-" + eventIndex + "-";
                if (!actualValue.toString().startsWith(expectedPrefix)) {
                    throw new ValueMatcherException("Path-specific tracking code mismatch", expectedPrefix + "*",
                            actualValue.toString());
                }
                return true;
            }
        };
        CustomComparator comparator = new CustomComparator(JSONCompareMode.STRICT,
                Customization.customization("events[*].trackingCode", pathAwareTrackingCodeMatcher));

        JSONAssert.assertEquals(expected, actual, comparator);

        JSONCompareResult result = JSONCompare.compareJSON(expected, actualWithWrongPathSpecificCode, comparator);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFieldFailures())
                .singleElement()
                .satisfies(failure -> {
                    assertThat(failure.getField())
                            .isEqualTo("events[1].trackingCode: Path-specific tracking code mismatch");
                    assertThat(failure.getExpected()).isEqualTo("tracking-1-*");
                    assertThat(failure.getActual()).isEqualTo("tracking-0-beta");
                });
    }

    @Test
    public void regularExpressionValueMatcherReportsExpectedAndActualValuesOnFailure() {
        RegularExpressionValueMatcher<Object> matcher = new RegularExpressionValueMatcher<>("[A-Z]{3}-\\d{2}");

        ValueMatcherException exception = assertThrows(ValueMatcherException.class,
                () -> matcher.equal("bad-value", "ignored-pattern"));

        assertThat(exception.getExpected()).isEqualTo("[A-Z]{3}-\\d{2}");
        assertThat(exception.getActual()).isEqualTo("bad-value");
        assertThat(exception.getMessage()).contains("Constant expected pattern did not match value");
    }

    @Test
    public void arraySizeComparatorValidatesExactAndRangedArrayLengths() throws JSONException {
        String actual = "{"
                + "\"numbers\":[1,2,3,4],"
                + "\"names\":[\"alpha\",\"beta\",\"gamma\"],"
                + "\"metadata\":{\"verified\":true}}";
        ArraySizeComparator comparator = new ArraySizeComparator(JSONCompareMode.LENIENT);

        JSONAssert.assertEquals("{\"numbers\":[4],\"names\":[2,3]}", actual, comparator);

        JSONCompareResult result = JSONCompare.compareJSON("{\"numbers\":[5]}", actual, comparator);

        assertThat(result.failed()).isTrue();
        assertThat(result.getMessage()).contains("numbers[]").contains("array size");
    }

    @Test
    public void arrayValueMatcherAppliesNestedComparatorToEveryArrayElementAndSelectedRanges() throws JSONException {
        String actual = """
                {
                  "rows": [
                    {"type": "row", "status": "ready", "rank": 1},
                    {"type": "row", "status": "ready", "rank": 2},
                    {"type": "row", "status": "ready", "rank": 3}
                  ]
                }
                """;
        JSONComparator lenientComparator = new DefaultComparator(JSONCompareMode.LENIENT);
        CustomComparator everyRowComparator = new CustomComparator(JSONCompareMode.LENIENT,
                new Customization("rows", new ArrayValueMatcher<>(lenientComparator)));
        CustomComparator selectedRowsComparator = new CustomComparator(JSONCompareMode.LENIENT,
                new Customization("rows", new ArrayValueMatcher<>(lenientComparator, 1, 2)));

        JSONAssert.assertEquals("{\"rows\":[{\"type\":\"row\"}]}", actual, everyRowComparator);
        JSONAssert.assertEquals("{\"rows\":[{\"status\":\"ready\"}]}", actual, selectedRowsComparator);

        AssertionError failure = assertThrows(AssertionError.class,
                () -> JSONAssert.assertEquals("{\"rows\":[{\"status\":\"done\"}]}", actual,
                        selectedRowsComparator));

        assertThat(failure.getMessage()).contains("rows[1].status").contains("done").contains("ready");
    }

    @Test
    public void parserHandlesTopLevelObjectsArraysAndPrimitiveJsonValues() throws JSONException {
        Object object = JSONParser.parseJSON("{\"id\":1}");
        Object array = JSONParser.parseJSON("[1,2,3]");
        Object number = JSONParser.parseJSON("42");
        Object string = JSONParser.parseJSON("\"quoted\"");

        assertThat(object).isInstanceOf(JSONObject.class);
        assertThat(array).isInstanceOf(JSONArray.class);
        assertThat(number).isInstanceOf(JSONString.class);
        assertThat(((JSONString) number).toJSONString()).isEqualTo("42");
        assertThat(string).isInstanceOf(JSONString.class);
        assertThat(((JSONString) string).toJSONString()).isEqualTo("\"quoted\"");
        assertThrows(JSONException.class, () -> JSONParser.parseJSON("not json"));
    }

    @Test
    public void compareJsonHandlesPrimitiveStringsAndMismatchedRootTypes() throws JSONException {
        assertThat(JSONCompare.compareJSON("42", "42", JSONCompareMode.STRICT).passed()).isTrue();
        assertThat(JSONCompare.compareJSON("\"alpha\"", "\"beta\"", JSONCompareMode.STRICT).failed()).isTrue();

        JSONCompareResult mismatchedRootTypes = JSONCompare.compareJSON("{\"id\":1}", "[1]", JSONCompareMode.LENIENT);

        assertThat(mismatchedRootTypes.failed()).isTrue();
        assertThat(mismatchedRootTypes.getFieldFailures())
                .singleElement()
                .satisfies(failure -> {
                    assertThat(failure.getField()).isEqualTo("");
                    assertThat(failure.getExpected()).isInstanceOf(JSONObject.class);
                    assertThat(failure.getActual()).isInstanceOf(JSONArray.class);
                });
    }

    @Test
    public void compareUtilitiesInspectArraysObjectsAndCardinality() throws JSONException {
        JSONArray objects = new JSONArray("["
                + "{\"id\":1,\"name\":\"alpha\"},{\"id\":2,\"name\":\"beta\"}]");
        JSONArray simpleValues = new JSONArray("[1,null,\"alpha\",true]");
        JSONArray nestedArrays = new JSONArray("[[1,2],[3,4]]");

        Map<Object, JSONObject> objectsById = JSONCompareUtil.arrayOfJsonObjectToMap(objects, "id");
        Map<String, Integer> cardinality = JSONCompareUtil.getCardinalityMap(
                Arrays.asList("a", "b", "a", "c", "b", "a"));

        assertThat(JSONCompareUtil.findUniqueKey(objects)).isEqualTo("id");
        assertThat(JSONCompareUtil.isUsableAsUniqueKey("id", objects)).isTrue();
        assertThat(JSONCompareUtil.isUsableAsUniqueKey("missing", objects)).isFalse();
        assertThat(objectsById.get(1).getString("name")).isEqualTo("alpha");
        assertThat(JSONCompareUtil.jsonArrayToList(simpleValues)).containsExactly(1, null, "alpha", Boolean.TRUE);
        assertThat(JSONCompareUtil.getObjectOrNull(simpleValues, 1)).isNull();
        assertThat(JSONCompareUtil.allSimpleValues(simpleValues)).isTrue();
        assertThat(JSONCompareUtil.allJSONObjects(objects)).isTrue();
        assertThat(JSONCompareUtil.allJSONArrays(nestedArrays)).isTrue();
        assertThat(JSONCompareUtil.getKeys(objects.getJSONObject(0))).containsExactly("id", "name");
        assertThat(JSONCompareUtil.qualify("catalog.items[0]", "name")).isEqualTo("catalog.items[0].name");
        assertThat(JSONCompareUtil.qualify("", "name")).isEqualTo("name");
        assertThat(JSONCompareUtil.formatUniqueKey("items", "id", 1)).isEqualTo("items[id=1]");
        assertThat(cardinality).containsEntry("a", 3).containsEntry("b", 2).containsEntry("c", 1);
    }
}
