/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_skyscreamer.jsonassert;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.ArrayValueMatcher;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.FieldComparisonFailure;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompare;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;
import org.skyscreamer.jsonassert.RegularExpressionValueMatcher;
import org.skyscreamer.jsonassert.comparator.ArraySizeComparator;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.skyscreamer.jsonassert.comparator.DefaultComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JsonassertTest {
    @Test
    void comparesObjectsWithBuiltInModes() throws Exception {
        String expected = """
                {
                  "id": 101,
                  "name": "Ada",
                  "tags": ["engineer", "speaker"],
                  "profile": {"active": true, "score": 9.5}
                }
                """;
        String actualWithExtraFieldsAndDifferentArrayOrder = """
                {
                  "profile": {"score": 9.5, "active": true},
                  "tags": ["speaker", "engineer"],
                  "name": "Ada",
                  "id": 101,
                  "department": "research"
                }
                """;

        JSONAssert.assertEquals(expected, actualWithExtraFieldsAndDifferentArrayOrder, JSONCompareMode.LENIENT);
        JSONAssert.assertEquals(expected, actualWithExtraFieldsAndDifferentArrayOrder, false);
        JSONAssert.assertNotEquals(expected, actualWithExtraFieldsAndDifferentArrayOrder, JSONCompareMode.STRICT);
        JSONAssert.assertNotEquals(expected, actualWithExtraFieldsAndDifferentArrayOrder, true);

        AssertionError nonExtensibleFailure = assertThrows(AssertionError.class,
                () -> JSONAssert.assertEquals(expected, actualWithExtraFieldsAndDifferentArrayOrder,
                        JSONCompareMode.NON_EXTENSIBLE));
        assertThat(nonExtensibleFailure).hasMessageContaining("department");

        AssertionError strictOrderFailure = assertThrows(AssertionError.class,
                () -> JSONAssert.assertEquals(expected, actualWithExtraFieldsAndDifferentArrayOrder,
                        JSONCompareMode.STRICT_ORDER));
        assertThat(strictOrderFailure).hasMessageContaining("tags");
    }

    @Test
    void exposesCompareModePropertiesAndDerivedModes() {
        assertThat(JSONCompareMode.STRICT.isExtensible()).isFalse();
        assertThat(JSONCompareMode.STRICT.hasStrictOrder()).isTrue();
        assertThat(JSONCompareMode.LENIENT.isExtensible()).isTrue();
        assertThat(JSONCompareMode.LENIENT.hasStrictOrder()).isFalse();
        assertThat(JSONCompareMode.NON_EXTENSIBLE.isExtensible()).isFalse();
        assertThat(JSONCompareMode.NON_EXTENSIBLE.hasStrictOrder()).isFalse();
        assertThat(JSONCompareMode.STRICT_ORDER.isExtensible()).isTrue();
        assertThat(JSONCompareMode.STRICT_ORDER.hasStrictOrder()).isTrue();

        assertThat(JSONCompareMode.LENIENT.withExtensible(false)).isSameAs(JSONCompareMode.NON_EXTENSIBLE);
        assertThat(JSONCompareMode.LENIENT.withStrictOrdering(true)).isSameAs(JSONCompareMode.STRICT_ORDER);
        assertThat(JSONCompareMode.STRICT.withExtensible(true)).isSameAs(JSONCompareMode.STRICT_ORDER);
        assertThat(JSONCompareMode.STRICT.withStrictOrdering(false)).isSameAs(JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void reportsFieldFailuresMissingFieldsAndUnexpectedFields() throws Exception {
        String expected = """
                {
                  "id": 1,
                  "required": true,
                  "nested": {"name": "primary"}
                }
                """;
        String actual = """
                {
                  "id": 2,
                  "nested": {},
                  "extra": "not expected"
                }
                """;

        JSONCompareResult result = JSONCompare.compareJSON(expected, actual, JSONCompareMode.NON_EXTENSIBLE);

        assertThat(result.failed()).isTrue();
        assertThat(result.passed()).isFalse();
        assertThat(result.isFailureOnField()).isTrue();
        assertThat(result.isMissingOnField()).isTrue();
        assertThat(result.isUnexpectedOnField()).isTrue();
        assertThat(result.getMessage()).contains("id", "required", "nested", "extra");
        assertThat(result.toString()).isEqualTo(result.getMessage());
        assertThat(result.getFieldFailures()).anySatisfy(failure -> {
            assertThat(failure.getField()).isEqualTo("id");
            assertThat(failure.getExpected()).isEqualTo(1);
            assertThat(failure.getActual()).isEqualTo(2);
        });
        assertThat(result.getFieldMissing()).extracting(FieldComparisonFailure::getExpected)
                .contains("required", "name");
        assertThat(result.getFieldUnexpected()).singleElement().satisfies(failure -> {
            assertThat(failure.getField()).isEmpty();
            assertThat(failure.getActual()).isEqualTo("extra");
        });
        assertThat(result.getField()).isEqualTo("id");
        assertThat(result.getExpected()).isEqualTo(1);
        assertThat(result.getActual()).isEqualTo(2);
    }

    @Test
    void comparesArraysAsJsonArraysAndJsonObjects() throws Exception {
        JSONArray expectedArray = new JSONArray("""
                [
                  {"id": 1, "roles": ["admin", "writer"]},
                  {"id": 2, "roles": ["reader"]}
                ]
                """);
        JSONArray reorderedArray = new JSONArray("""
                [
                  {"roles": ["reader"], "id": 2, "ignored": true},
                  {"roles": ["writer", "admin"], "id": 1}
                ]
                """);
        JSONObject expectedObject = new JSONObject("""
                {"payload": {"ok": true}, "items": [1, 2, 3]}
                """);
        JSONObject actualObject = new JSONObject("""
                {"items": [3, 2, 1], "payload": {"ok": true}, "extra": "allowed"}
                """);

        JSONAssert.assertEquals(expectedArray, reorderedArray, JSONCompareMode.LENIENT);
        JSONAssert.assertEquals("JSONObject overload should accept extensible unordered content",
                expectedObject, actualObject, JSONCompareMode.LENIENT);
        JSONAssert.assertNotEquals(expectedArray, reorderedArray, JSONCompareMode.STRICT_ORDER);

        JSONCompareResult arrayResult = JSONCompare.compareJSON(expectedArray, reorderedArray, JSONCompareMode.LENIENT);
        JSONCompareResult objectResult = JSONCompare.compareJSON(expectedObject, actualObject, JSONCompareMode.LENIENT);

        assertThat(arrayResult.passed()).isTrue();
        assertThat(objectResult.passed()).isTrue();
    }

    @Test
    void appliesCustomComparatorsForPatternsAndNumericTolerance() throws Exception {
        String expected = """
                {
                  "eventId": "EVT-[0-9]{3}",
                  "payload": {
                    "createdAt": "ignored by static regex",
                    "durationMs": 1000,
                    "status": "OK"
                  },
                  "metrics": {"cpu": 1.0, "memory": 2048.0}
                }
                """;
        String actual = """
                {
                  "eventId": "EVT-731",
                  "payload": {
                    "createdAt": "2024-05-02T10:15:30Z",
                    "durationMs": 1003,
                    "status": "OK",
                    "source": "sensor-a"
                  },
                  "metrics": {"cpu": 1.2, "memory": 2047.8}
                }
                """;
        JSONComparator comparator = new CustomComparator(JSONCompareMode.LENIENT,
                Customization.customization("eventId", new RegularExpressionValueMatcher<>()),
                Customization.customization("payload.createdAt",
                        new RegularExpressionValueMatcher<>("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")),
                Customization.customization("payload.durationMs", JsonassertTest::withinFive),
                Customization.customization("metrics.cpu", JsonassertTest::withinQuarter),
                Customization.customization("metrics.memory", JsonassertTest::withinQuarter));

        JSONAssert.assertEquals(expected, actual, comparator);

        String actualWithBadIdentifier = actual.replace("EVT-731", "event-731");
        JSONCompareResult failedRegexResult = JSONCompare.compareJSON(expected, actualWithBadIdentifier, comparator);

        assertThat(failedRegexResult.failed()).isTrue();
        assertThat(failedRegexResult.getMessage()).contains("eventId", "expected pattern did not match");
        assertThat(failedRegexResult.getFieldFailures()).singleElement().satisfies(failure -> {
            assertThat(failure.getField()).startsWith("eventId");
            assertThat(failure.getExpected()).isEqualTo("EVT-[0-9]{3}");
            assertThat(failure.getActual()).isEqualTo("event-731");
        });
    }

    @Test
    void validatesArraySizesAndArrayElementsWithSpecializedComparators() throws Exception {
        JSONComparator arraySizeComparator = new ArraySizeComparator(JSONCompareMode.LENIENT);

        JSONCompareResult exactSizeResult = JSONCompare.compareJSON("[3]", "[10, 20, 30]", arraySizeComparator);
        JSONCompareResult rangedSizeResult = JSONCompare.compareJSON("[2, 4]", "[10, 20, 30]", arraySizeComparator);
        JSONCompareResult failedSizeResult = JSONCompare.compareJSON("[4, 6]", "[10, 20, 30]", arraySizeComparator);

        assertThat(exactSizeResult.passed()).isTrue();
        assertThat(rangedSizeResult.passed()).isTrue();
        assertThat(failedSizeResult.failed()).isTrue();
        assertThat(failedSizeResult.getMessage()).contains("array size of 4 to 6 elements", "3 elements");

        JSONComparator repeatingItemComparator = new CustomComparator(JSONCompareMode.LENIENT,
                Customization.customization("items",
                        new ArrayValueMatcher<>(new DefaultComparator(JSONCompareMode.LENIENT), 0, 2)));
        String expectedRepeatingItemShape = """
                {"items": [{"type": "book", "available": true}]}
                """;
        String actualRepeatingItems = """
                {
                  "items": [
                    {"type": "book", "available": true, "id": 1},
                    {"type": "book", "available": true, "id": 2},
                    {"type": "book", "available": true, "id": 3}
                  ]
                }
                """;
        String actualWithDifferentItem = actualRepeatingItems.replace(
                "\"type\": \"book\", \"available\": true, \"id\": 2",
                "\"type\": \"magazine\", \"available\": true, \"id\": 2");

        JSONAssert.assertEquals(expectedRepeatingItemShape, actualRepeatingItems, repeatingItemComparator);
        JSONCompareResult failedItemResult = JSONCompare.compareJSON(expectedRepeatingItemShape,
                actualWithDifferentItem, repeatingItemComparator);

        assertThat(failedItemResult.failed()).isTrue();
        assertThat(failedItemResult.getMessage()).contains("items[1].type", "book", "magazine");
    }

    private static boolean withinFive(Object actualValue, Object expectedValue) {
        return Math.abs(numberValue(actualValue) - numberValue(expectedValue)) <= 5.0;
    }

    private static boolean withinQuarter(Object actualValue, Object expectedValue) {
        return Math.abs(numberValue(actualValue) - numberValue(expectedValue)) <= 0.25;
    }

    private static double numberValue(Object value) {
        return ((Number) value).doubleValue();
    }
}
