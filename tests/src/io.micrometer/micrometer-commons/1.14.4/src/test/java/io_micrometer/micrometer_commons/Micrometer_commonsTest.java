/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_commons;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.common.docs.KeyName;
import io.micrometer.common.util.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Micrometer_commonsTest {

    @Test
    void keyValueFactoriesCreateComparableImmutablePairs() {
        KeyValue statusOk = KeyValue.of("status", "ok");
        KeyValue sameStatus = KeyValue.of("status", "ok");
        KeyValue uri = KeyValue.of(TestKeyName.URI, "/orders");
        KeyValue mapped = KeyValue.of(
                new MetricDimension("method", "GET"), MetricDimension::key, MetricDimension::value);

        assertThat(statusOk.getKey()).isEqualTo("status");
        assertThat(statusOk.getValue()).isEqualTo("ok");
        assertThat(statusOk).isEqualTo(sameStatus).hasSameHashCodeAs(sameStatus);
        assertThat(statusOk.toString()).isEqualTo("keyValue(status=ok)");
        assertThat(statusOk.compareTo(uri)).isNegative();
        assertThat(uri.getKey()).isEqualTo("uri");
        assertThat(uri.getValue()).isEqualTo("/orders");
        assertThat(mapped).isEqualTo(KeyValue.of("method", "GET"));
        assertThat(KeyValue.NONE_VALUE).isEqualTo("none");
    }

    @Test
    void keyValueRejectsNullImmutableComponents() {
        assertThatThrownBy(() -> KeyValue.of((String) null, "value")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> KeyValue.of("key", (String) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatedKeyValueConvertsAcceptedValuesAndRejectsInvalidValues() {
        KeyValue successfulStatus = KeyValue.of("status.code", 200, code -> code >= 100 && code < 600);
        KeyValue successfulName = TestKeyName.METHOD.withValue(
                "POST", value -> value instanceof String && !value.toString().isEmpty());

        assertThat(successfulStatus.getKey()).isEqualTo("status.code");
        assertThat(successfulStatus.getValue()).isEqualTo("200");
        assertThat(successfulStatus.toString()).isEqualTo("keyValue(status.code=200)");
        assertThat(successfulName.getKey()).isEqualTo("method");
        assertThat(successfulName.getValue()).isEqualTo("POST");
        assertThatThrownBy(() -> KeyValue.of("status.code", 99, code -> code >= 100 && code < 600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Argument [99]")
                .hasMessageContaining("key [status.code]");
    }

    @Test
    void keyNamesExposeDefaultsMergeAndCreateKeyValues() {
        KeyName[] merged = KeyName.merge(TestKeyName.values(), OptionalKeyName.values());

        assertThat(Arrays.stream(merged).map(KeyName::asString).collect(Collectors.toList()))
                .containsExactly("method", "uri", "outcome");
        assertThat(TestKeyName.METHOD.isRequired()).isTrue();
        assertThat(OptionalKeyName.OUTCOME.isRequired()).isFalse();
        assertThat(TestKeyName.URI.withValue("/actuator")).isEqualTo(KeyValue.of("uri", "/actuator"));
    }

    @Test
    void keyValuesCreatedFromStringsAreSortedDeduplicatedAndIterable() {
        KeyValues keyValues = KeyValues.of("status", "200", "method", "GET", "status", "201", "uri", "/orders");

        assertThat(keys(keyValues)).containsExactly("method", "status", "uri");
        assertThat(values(keyValues)).containsExactly("GET", "201", "/orders");
        assertThat(keyValues.toString()).isEqualTo("[keyValue(method=GET),keyValue(status=201),keyValue(uri=/orders)]");
        assertThat(keyValues.stream().map(KeyValue::getKey).collect(Collectors.joining(",")))
                .isEqualTo("method,status,uri");
        assertThat(StreamSupport.stream(keyValues.spliterator(), false)
                .map(KeyValue::getValue)
                .collect(Collectors.toList())).containsExactly("GET", "201", "/orders");
    }

    @Test
    void keyValuesEmptyAndNullVarargsReturnReusableEmptySet() {
        KeyValues empty = KeyValues.empty();

        assertThat(empty).isSameAs(KeyValues.empty());
        assertThat(KeyValues.of((String[]) null)).isSameAs(empty);
        assertThat(KeyValues.of(new String[0])).isSameAs(empty);
        assertThat(KeyValues.of((KeyValue[]) null)).isSameAs(empty);
        assertThat(KeyValues.of(new KeyValue[0])).isSameAs(empty);
        assertThat(KeyValues.of((Iterable<KeyValue>) null)).isSameAs(empty);
        assertThat(empty.and((String[]) null)).isSameAs(empty);
        assertThat(empty.and(new String[0])).isSameAs(empty);
        assertThat(empty.and((KeyValue[]) null)).isSameAs(empty);
        assertThat(empty.and(new KeyValue[0])).isSameAs(empty);
        assertThat(empty.and((Iterable<KeyValue>) null)).isSameAs(empty);
        assertThat(empty.iterator().hasNext()).isFalse();
    }

    @Test
    void keyValuesRejectOddStringVarargsAndImmutableIteratorRemoval() {
        Iterator<KeyValue> iterator = KeyValues.of("method", "GET").iterator();

        assertThatThrownBy(() -> KeyValues.of("method", "GET", "status"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be even, it is a set of key=value pairs");
        assertThat(iterator.next()).isEqualTo(KeyValue.of("method", "GET"));
        assertThatThrownBy(iterator::remove)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("cannot remove items from key values");
    }

    @Test
    void keyValuesMergeConcatenateAndMapIterableInputs() {
        List<MetricDimension> dimensions = Arrays.asList(
                new MetricDimension("method", "GET"),
                new MetricDimension("uri", "/orders"));
        KeyValues first = KeyValues.of("status", "200", "method", "POST");
        KeyValues second = KeyValues.of(dimensions, MetricDimension::key, MetricDimension::value);

        KeyValues merged = first.and(second).and("exception", "none");
        KeyValues concatenated = KeyValues.concat(KeyValues.of("alpha", "a"), KeyValues.of("beta", "b"));
        KeyValues concatenatedWithStrings = KeyValues.concat(
                KeyValues.of("alpha", "a"), "beta", "b", "alpha", "override");

        assertThat(keys(merged)).containsExactly("exception", "method", "status", "uri");
        assertThat(values(merged)).containsExactly("none", "GET", "200", "/orders");
        assertThat(concatenated).isEqualTo(KeyValues.of("alpha", "a", "beta", "b"));
        assertThat(concatenatedWithStrings).isEqualTo(KeyValues.of("alpha", "override", "beta", "b"));
        assertThat(first.and(new ArrayList<MetricDimension>(), MetricDimension::key, MetricDimension::value))
                .isSameAs(first);
    }

    @Test
    void keyValuesExposeDistinctSortedNonnullImmutableSpliterator() {
        Spliterator<KeyValue> spliterator = KeyValues.of("status", "200", "method", "GET").spliterator();

        assertThat(spliterator.hasCharacteristics(Spliterator.DISTINCT)).isTrue();
        assertThat(spliterator.hasCharacteristics(Spliterator.SORTED)).isTrue();
        assertThat(spliterator.hasCharacteristics(Spliterator.NONNULL)).isTrue();
        assertThat(spliterator.hasCharacteristics(Spliterator.IMMUTABLE)).isTrue();
        assertThat(spliterator.getComparator()).isNull();
        assertThat(spliterator.getExactSizeIfKnown()).isEqualTo(2);
    }

    @Test
    void stringUtilsClassifiesBlankAndEmptyStrings() {
        assertThat(StringUtils.isEmpty(null)).isTrue();
        assertThat(StringUtils.isEmpty("")).isTrue();
        assertThat(StringUtils.isEmpty(" ")).isFalse();
        assertThat(StringUtils.isNotEmpty("micrometer")).isTrue();
        assertThat(StringUtils.isBlank(null)).isTrue();
        assertThat(StringUtils.isBlank(" \t\n")).isTrue();
        assertThat(StringUtils.isBlank(" metrics ")).isFalse();
        assertThat(StringUtils.isNotBlank("metrics")).isTrue();
        assertThat(StringUtils.isNotBlank("\r\n")).isFalse();
    }

    @Test
    void stringUtilsTruncatesWithAndWithoutIndicators() {
        assertThat(StringUtils.truncate("micrometer", 5)).isEqualTo("micro");
        assertThat(StringUtils.truncate("micro", 10)).isEqualTo("micro");
        assertThat(StringUtils.truncate("micrometer", 7, "...")).isEqualTo("micr...");
        assertThat(StringUtils.truncate("micro", 7, "...")).isEqualTo("micro");
        assertThatThrownBy(() -> StringUtils.truncate("micrometer", 3, "..."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxLength must be greater than length of truncationIndicator");
    }

    @Test
    void valueResolversCanResolveArgumentsAndExpressions() {
        ValueResolver upperCaseResolver = argument -> argument == null ? "missing" : argument.toString().toUpperCase();
        ValueExpressionResolver expressionResolver = (expression, argument) -> expression + ":" + argument;

        assertThat(upperCaseResolver.resolve("timer")).isEqualTo("TIMER");
        assertThat(upperCaseResolver.resolve(null)).isEqualTo("missing");
        assertThat(expressionResolver.resolve("name", "counter")).isEqualTo("name:counter");
        assertThat(new NoOpValueResolver().resolve("anything")).isNull();
    }

    private static List<String> keys(KeyValues keyValues) {
        return keyValues.stream().map(KeyValue::getKey).collect(Collectors.toList());
    }

    private static List<String> values(KeyValues keyValues) {
        return keyValues.stream().map(KeyValue::getValue).collect(Collectors.toList());
    }

    private enum TestKeyName implements KeyName {
        METHOD("method"),
        URI("uri");

        private final String key;

        TestKeyName(String key) {
            this.key = key;
        }

        @Override
        public String asString() {
            return key;
        }
    }

    private enum OptionalKeyName implements KeyName {
        OUTCOME("outcome");

        private final String key;

        OptionalKeyName(String key) {
            this.key = key;
        }

        @Override
        public String asString() {
            return key;
        }

        @Override
        public boolean isRequired() {
            return false;
        }
    }

    private static final class MetricDimension {
        private final String key;
        private final String value;

        private MetricDimension(String key, String value) {
            this.key = key;
            this.value = value;
        }

        private String key() {
            return key;
        }

        private String value() {
            return value;
        }
    }
}
