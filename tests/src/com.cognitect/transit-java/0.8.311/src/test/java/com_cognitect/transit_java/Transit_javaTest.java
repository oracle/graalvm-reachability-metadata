/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_cognitect.transit_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.cognitect.transit.DefaultReadHandler;
import com.cognitect.transit.Keyword;
import com.cognitect.transit.Link;
import com.cognitect.transit.Ratio;
import com.cognitect.transit.ReadHandler;
import com.cognitect.transit.Reader;
import com.cognitect.transit.Symbol;
import com.cognitect.transit.TaggedValue;
import com.cognitect.transit.TransitFactory;
import com.cognitect.transit.URI;
import com.cognitect.transit.WriteHandler;
import com.cognitect.transit.Writer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class Transit_javaTest {
    private static final List<TransitFactory.Format> ALL_FORMATS = Arrays.asList(
            TransitFactory.Format.JSON,
            TransitFactory.Format.JSON_VERBOSE,
            TransitFactory.Format.MSGPACK);

    @Test
    void roundTripsScalarsAndTransitValueTypesInEveryFormat() {
        for (TransitFactory.Format format : ALL_FORMATS) {
            Keyword keyword = TransitFactory.keyword("example/status");
            Symbol symbol = TransitFactory.symbol("example/symbol");
            URI uri = TransitFactory.uri("https://example.test/items/42?expand=true");
            UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
            BigInteger bigInteger = new BigInteger("123456789012345678901234567890");
            BigDecimal bigDecimal = new BigDecimal("1234567890.0123456789");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("null", null);
            payload.put("boolean", Boolean.TRUE);
            payload.put("integer", 42);
            payload.put("long", 9_007_199_254_740_991L);
            payload.put("bigInteger", bigInteger);
            payload.put("bigDecimal", bigDecimal);
            payload.put("double", 123.5D);
            payload.put("positiveInfinity", Double.POSITIVE_INFINITY);
            payload.put("negativeInfinity", Double.NEGATIVE_INFINITY);
            payload.put("notANumber", Double.NaN);
            payload.put("string", "hello transit");
            payload.put("keyword", keyword);
            payload.put("symbol", symbol);
            payload.put("uri", uri);
            payload.put("uuid", uuid);
            payload.put("binary", "binary-data".getBytes(StandardCharsets.UTF_8));

            Map<?, ?> result = roundTrip(format, payload);

            assertThat(result.get("null")).isNull();
            assertThat(result.get("boolean")).isEqualTo(true);
            assertThat(result.get("integer")).isEqualTo(42L);
            assertThat(result.get("long")).isEqualTo(9_007_199_254_740_991L);
            assertThat(result.get("bigInteger")).isEqualTo(bigInteger);
            assertThat(result.get("bigDecimal")).isEqualTo(bigDecimal);
            assertThat(result.get("double")).isEqualTo(123.5D);
            assertThat(result.get("positiveInfinity")).isEqualTo(Double.POSITIVE_INFINITY);
            assertThat(result.get("negativeInfinity")).isEqualTo(Double.NEGATIVE_INFINITY);
            assertThat((Double) result.get("notANumber")).isNaN();
            assertThat(result.get("string")).isEqualTo("hello transit");
            assertThat(result.get("keyword")).isEqualTo(keyword);
            assertThat(result.get("symbol")).isEqualTo(symbol);
            assertThat(result.get("uri")).isEqualTo(uri);
            assertThat(result.get("uuid")).isEqualTo(uuid);
            assertThat((byte[]) result.get("binary")).containsExactly("binary-data".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void roundTripsNestedCollectionsAndCompoundMapKeysInEveryFormat() {
        for (TransitFactory.Format format : ALL_FORMATS) {
            Keyword alpha = TransitFactory.keyword("alpha");
            Keyword beta = TransitFactory.keyword("ns/beta");
            Set<Object> set = new LinkedHashSet<>(Arrays.asList("first", alpha, 7L));
            Map<Object, Object> nestedMap = new LinkedHashMap<>();
            nestedMap.put(alpha, Arrays.asList(1L, 2L, 3L));
            nestedMap.put(beta, set);
            nestedMap.put(Arrays.asList("compound", "key"), Collections.singletonMap("nested", true));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("list", Arrays.asList("a", 1, false, nestedMap));
            payload.put("set", set);
            payload.put("colors", Arrays.asList("red", "green", "blue"));
            payload.put("map", nestedMap);

            Map<?, ?> result = roundTrip(format, payload);

            assertThat(result.get("list")).isInstanceOf(List.class);
            List<?> list = (List<?>) result.get("list");
            assertThat(list).hasSize(4);
            assertThat(list.subList(0, 3)).isEqualTo(Arrays.asList("a", 1L, false));
            assertThat(list.get(3)).isEqualTo(nestedMap);
            assertThat(result.get("set")).isEqualTo(set);
            assertThat(result.get("colors")).isEqualTo(Arrays.asList("red", "green", "blue"));
            assertThat(result.get("map")).isEqualTo(nestedMap);
        }
    }

    @Test
    void writesObjectAndPrimitiveArraysAsTransitArraysInEveryFormat() {
        for (TransitFactory.Format format : ALL_FORMATS) {
            Keyword keyword = TransitFactory.keyword("array/item");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("objectArray", new Object[] {"alpha", keyword, 3});
            payload.put("intArray", new int[] {1, 2, 3});
            payload.put("booleanArray", new boolean[] {true, false, true});
            payload.put("charArray", new char[] {'a', 'z'});

            Map<?, ?> result = roundTrip(format, payload);

            assertThat(result.get("objectArray")).isEqualTo(Arrays.asList("alpha", keyword, 3L));
            assertThat(result.get("intArray")).isEqualTo(Arrays.asList(1L, 2L, 3L));
            assertThat(result.get("booleanArray")).isEqualTo(Arrays.asList(true, false, true));
            assertThat(result.get("charArray")).isEqualTo(Arrays.asList('a', 'z'));
        }
    }

    @Test
    void readsTransitLiteralsForRatiosLinksAndUnknownTaggedValues() {
        Map<?, ?> payload = read(TransitFactory.Format.JSON_VERBOSE, """
                {
                  "ratio": ["~#ratio", ["~n6", "~n3"]],
                  "link": ["~#link", ["^ ",
                    "href", "~rhttps://example.test/api",
                    "rel", "item",
                    "name", "Example",
                    "prompt", "Open",
                    "render", "link"]],
                  "unknown": ["~#custom/tag", ["value", "~i99"]]
                }
                """);

        Ratio ratio = (Ratio) payload.get("ratio");
        assertThat(ratio.getNumerator()).isEqualTo(BigInteger.valueOf(6));
        assertThat(ratio.getDenominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(ratio.getValue()).isEqualTo(2.0D);

        Link link = (Link) payload.get("link");
        assertThat(link.getHref()).isEqualTo(TransitFactory.uri("https://example.test/api"));
        assertThat(link.getRel()).isEqualTo("item");
        assertThat(link.getName()).isEqualTo("Example");
        assertThat(link.getPrompt()).isEqualTo("Open");
        assertThat(link.getRender()).isEqualTo("link");

        TaggedValue<?> unknown = (TaggedValue<?>) payload.get("unknown");
        assertThat(unknown.getTag()).isEqualTo("custom/tag");
        assertThat(unknown.getRep()).isEqualTo(Arrays.asList("value", 99L));
    }

    @Test
    void writesJavaNetUrisAsTransitUrisInEveryFormat() {
        java.net.URI javaUri = java.net.URI.create("https://example.test/search?q=transit#result");
        URI transitUri = TransitFactory.uri(javaUri.toString());
        for (TransitFactory.Format format : ALL_FORMATS) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("uri", javaUri);

            Map<?, ?> result = roundTrip(format, payload);

            assertThat(result.get("uri")).isEqualTo(transitUri);
        }
    }

    @Test
    void writesAndReadsLinksAndExplicitTaggedValuesInEveryFormat() {
        for (TransitFactory.Format format : ALL_FORMATS) {
            Link fullLink = TransitFactory.link(
                    TransitFactory.uri("https://example.test/orders/123"), "self", "Order 123", "link", "Open order");
            Link simpleLink = TransitFactory.link("https://example.test/orders", "collection");
            TaggedValue<List<String>> taggedValue = TransitFactory.taggedValue(
                    "app/color", Arrays.asList("red", "green"));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("fullLink", fullLink);
            payload.put("simpleLink", simpleLink);
            payload.put("taggedValue", taggedValue);

            Map<?, ?> result = roundTrip(format, payload);

            Link fullResult = (Link) result.get("fullLink");
            assertThat(fullResult.getHref()).isEqualTo(fullLink.getHref());
            assertThat(fullResult.getRel()).isEqualTo("self");
            assertThat(fullResult.getName()).isEqualTo("Order 123");
            assertThat(fullResult.getPrompt()).isEqualTo("Open order");
            assertThat(fullResult.getRender()).isEqualTo("link");

            Link simpleResult = (Link) result.get("simpleLink");
            assertThat(simpleResult.getHref()).isEqualTo(simpleLink.getHref());
            assertThat(simpleResult.getRel()).isEqualTo("collection");

            TaggedValue<?> taggedResult = (TaggedValue<?>) result.get("taggedValue");
            assertThat(taggedResult.getTag()).isEqualTo("app/color");
            assertThat(taggedResult.getRep()).isEqualTo(Arrays.asList("red", "green"));
        }
    }

    @Test
    void customWriteAndReadHandlersConvertApplicationTypesInEveryFormat() {
        for (TransitFactory.Format format : ALL_FORMATS) {
            Map<Class, WriteHandler<?, ?>> pointWriteHandlers = new LinkedHashMap<>();
            pointWriteHandlers.put(Point.class, new PointWriteHandler());
            Map<Class, WriteHandler<?, ?>> writeHandlers = TransitFactory.writeHandlerMap(pointWriteHandlers);
            Map<String, ReadHandler<?, ?>> pointReadHandlers = new LinkedHashMap<>();
            pointReadHandlers.put("point", new PointReadHandler());
            Map<String, ReadHandler<?, ?>> readHandlers = TransitFactory.readHandlerMap(pointReadHandlers);
            Point point = new Point(12, -4);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Writer<Object> writer = TransitFactory.writer(format, output, writeHandlers);
            writer.write(Collections.singletonMap("point", point));

            Reader reader = TransitFactory.reader(format, new ByteArrayInputStream(output.toByteArray()), readHandlers);
            Map<?, ?> result = reader.read();

            assertThat(result.get("point")).isEqualTo(point);
        }
    }

    @Test
    void defaultReadHandlerReceivesTagsWithoutRegisteredHandlers() {
        DefaultReadHandler<UnrecognizedTaggedValue> defaultReadHandler = UnrecognizedTaggedValue::new;
        byte[] input = "[\"~#missing/tag\",{\"name\":\"sample\",\"count\":\"~i3\"}]"
                .getBytes(StandardCharsets.UTF_8);

        Reader reader = TransitFactory.reader(
                TransitFactory.Format.JSON,
                new ByteArrayInputStream(input),
                Collections.emptyMap(),
                defaultReadHandler);
        UnrecognizedTaggedValue value = reader.read();

        assertThat(value.tag()).isEqualTo("missing/tag");
        assertThat(value.representation()).isEqualTo(mapOf("name", "sample", "count", 3L));
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(TransitFactory.Format format, Object payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Writer<Object> writer = TransitFactory.writer(format, output);
        writer.write(payload);

        Reader reader = TransitFactory.reader(format, new ByteArrayInputStream(output.toByteArray()));
        return (T) reader.read();
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(TransitFactory.Format format, String input) {
        Reader reader = TransitFactory.reader(format, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        return (T) reader.read();
    }

    private static Map<String, Object> mapOf(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(firstKey, firstValue);
        map.put(secondKey, secondValue);
        return map;
    }

    private static final class Point {
        private final int x;
        private final int y;

        private Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Point)) {
                return false;
            }
            Point point = (Point) other;
            return x == point.x && y == point.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }

    private static final class PointWriteHandler implements WriteHandler<Point, List<Integer>> {
        @Override
        public String tag(Point point) {
            return "point";
        }

        @Override
        public List<Integer> rep(Point point) {
            return Arrays.asList(point.x, point.y);
        }

        @Override
        public String stringRep(Point point) {
            return point.x + "," + point.y;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> WriteHandler<Point, V> getVerboseHandler() {
            return (WriteHandler<Point, V>) this;
        }
    }

    private static final class PointReadHandler implements ReadHandler<Point, List<?>> {
        @Override
        public Point fromRep(List<?> representation) {
            return new Point(
                    ((Number) representation.get(0)).intValue(),
                    ((Number) representation.get(1)).intValue());
        }
    }

    private static final class UnrecognizedTaggedValue {
        private final String tag;
        private final Object representation;

        private UnrecognizedTaggedValue(String tag, Object representation) {
            this.tag = tag;
            this.representation = representation;
        }

        private String tag() {
            return tag;
        }

        private Object representation() {
            return representation;
        }
    }
}
