/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_selector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.selector.filter.BooleanExpression;
import org.apache.activemq.artemis.selector.filter.FilterException;
import org.apache.activemq.artemis.selector.filter.Filterable;
import org.apache.activemq.artemis.selector.filter.XPathExpression;
import org.apache.activemq.artemis.selector.impl.LRUCache;
import org.apache.activemq.artemis.selector.impl.SelectorParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Artemis_selectorTest {
    @Test
    void evaluatesComparisonsLogicAndArithmeticSelectors() throws Exception {
        TestFilterable message = filterable(Map.of(
                "priority", 8,
                "category", "orders",
                "price", 10,
                "quantity", 7,
                "adjustment", 5,
                "active", true));

        BooleanExpression selector = SelectorParser.parse("""
                priority >= 5
                AND category = 'orders'
                AND active = TRUE
                AND ((price * quantity) + adjustment) / 2 >= 37.5
                AND price % 2 = 0
                """);

        assertThat(selector.evaluate(message)).isEqualTo(Boolean.TRUE);
        assertThat(selector.matches(message)).isTrue();
        assertThat(SelectorParser.parse("NOT (priority < 10 OR category <> 'orders')").matches(message)).isFalse();
    }

    @Test
    void evaluatesSetRangePatternAndNullOperators() throws Exception {
        TestFilterable matching = filterable(Map.of(
                "status", "S5",
                "symbol", "A_2024",
                "score", 42,
                "name", "consumer"));
        TestFilterable nonMatching = filterable(Map.of(
                "status", "S7",
                "symbol", "AB2024",
                "score", 101,
                "name", "consumer"));

        BooleanExpression selector = SelectorParser.parse("""
                status IN ('S1', 'S2', 'S3', 'S4', 'S5')
                AND symbol LIKE 'A!_%' ESCAPE '!'
                AND score BETWEEN 40 AND 50
                AND name IS NOT NULL
                AND missing IS NULL
                """);

        assertThat(selector.matches(matching)).isTrue();
        assertThat(selector.matches(nonMatching)).isFalse();
        assertThat(SelectorParser.parse("status NOT IN ('S1', 'S2') AND score NOT BETWEEN 1 AND 10")
                .matches(matching)).isTrue();
        assertThat(SelectorParser.parse("missing = 'value'").evaluate(matching)).isNull();
    }

    @Test
    void honorsParserPrefixesForStringConversionAndHyphenatedProperties() throws Exception {
        TestFilterable message = filterable(Map.of(
                "age", "30",
                "enabled", "true",
                "order-id", "A-42"));

        assertThat(SelectorParser.parse("age > 20 OR enabled = TRUE").matches(message)).isFalse();
        assertThat(SelectorParser.parse("convert_string_expressions:age > 20 AND enabled = TRUE")
                .matches(message)).isTrue();
        assertThat(SelectorParser.parse("hyphenated_props:order-id = 'A-42'").matches(message)).isTrue();
    }

    @Test
    void decodesEscapedApostrophesInStringLiterals() throws Exception {
        TestFilterable message = filterable(Map.of("customer", "Bob's Bikes"));

        BooleanExpression selector = SelectorParser.parse("customer = 'Bob''s Bikes'");

        assertThat(selector.evaluate(message)).isEqualTo(Boolean.TRUE);
        assertThat(selector.matches(message)).isTrue();
        assertThat(SelectorParser.parse("customer = 'Bobs Bikes'").matches(message)).isFalse();
    }

    @Test
    void supportsQuotedIdentifiersAndXmlBodySelectors() throws Exception {
        TestFilterable message = filterable(
                Map.of("quoted property", "expected"),
                """
                        <order>
                            <customer tier="gold">Ada</customer>
                            <total>19.99</total>
                        </order>
                        """);

        assertThat(SelectorParser.parse("\"quoted property\" = 'expected'").matches(message)).isTrue();
        assertThat(SelectorParser.parse("XPATH '/order/customer[@tier=\"gold\"]'").matches(message)).isTrue();
        assertThat(SelectorParser.parse("XPATH '/order/customer[@tier=\"silver\"]'").matches(message)).isFalse();
        assertThat(SelectorParser.parse("XQUERY '/order/customer'").matches(message)).isFalse();
    }

    @Test
    void reportsInvalidSelectorsAsFilterExceptions() {
        assertThatExceptionOfType(FilterException.class)
                .isThrownBy(() -> SelectorParser.parse("priority >"))
                .withMessageContaining("priority >");
    }

    @Test
    void delegatesXPathSelectorsToConfiguredEvaluatorFactory() throws Exception {
        XPathExpression.XPathEvaluatorFactory originalFactory = XPathExpression.XPATH_EVALUATOR_FACTORY;
        AtomicReference<String> requestedXPath = new AtomicReference<>();

        try {
            XPathExpression.XPATH_EVALUATOR_FACTORY = xpath -> {
                requestedXPath.set(xpath);
                return message -> "accepted".equals(message.getProperty(SimpleString.toSimpleString("routing")));
            };

            BooleanExpression selector = SelectorParser.parse("XPATH 'routing-check'");
            TestFilterable accepted = filterable(Map.of("routing", "accepted"));
            TestFilterable rejected = filterable(Map.of("routing", "rejected"));

            assertThat(requestedXPath).hasValue("routing-check");
            assertThat(selector.evaluate(accepted)).isEqualTo(Boolean.TRUE);
            assertThat(selector.matches(accepted)).isTrue();
            assertThat(selector.matches(rejected)).isFalse();
            assertThat(selector.toString()).isEqualTo("XPATH 'routing-check'");
        } finally {
            XPathExpression.XPATH_EVALUATOR_FACTORY = originalFactory;
        }
    }

    @Test
    void evictsLeastRecentlyUsedEntriesFromCache() {
        RecordingCache<String, Integer> cache = new RecordingCache<>(2, true);

        cache.put("one", 1);
        cache.put("two", 2);
        assertThat(cache.get("one")).isEqualTo(1);
        cache.put("three", 3);

        assertThat(cache).containsOnlyKeys("one", "three");
        assertThat(cache.evictedKeys).containsExactly("two");
        assertThat(cache.getMaxCacheSize()).isEqualTo(2);

        cache.setMaxCacheSize(1);
        cache.clear();
        cache.put("four", 4);
        cache.put("five", 5);

        assertThat(cache).containsOnlyKeys("five");
        assertThat(cache.evictedKeys).containsExactly("two", "four");
    }

    private static TestFilterable filterable(Map<String, Object> properties) {
        return filterable(properties, null);
    }

    private static TestFilterable filterable(Map<String, Object> properties, String body) {
        return new TestFilterable(properties, body);
    }

    private static final class TestFilterable implements Filterable {
        private final Map<String, Object> properties;
        private final String body;

        private TestFilterable(Map<String, Object> properties, String body) {
            this.properties = properties;
            this.body = body;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getBodyAs(Class<T> type) {
            if (type == String.class) {
                return (T) body;
            }
            return null;
        }

        @Override
        public Object getProperty(SimpleString name) {
            return properties.get(name.toString());
        }

        @Override
        public Object getLocalConnectionId() {
            return "test-connection";
        }
    }

    private static final class RecordingCache<K, V> extends LRUCache<K, V> {
        private final List<K> evictedKeys = new ArrayList<>();

        private RecordingCache(int maximumCacheSize, boolean accessOrder) {
            super(0, maximumCacheSize, 0.75f, accessOrder);
        }

        @Override
        protected void onCacheEviction(Map.Entry<K, V> eldest) {
            evictedKeys.add(eldest.getKey());
        }
    }
}
