/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_java8_compat_2_13;

import org.junit.jupiter.api.Test;
import scala.Tuple2;
import scala.collection.mutable.Buffer;
import scala.collection.mutable.Map;
import scala.compat.java8.ScalaStreamSupport;
import scala.jdk.javaapi.CollectionConverters;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScalaStreamSupportTest {
    @Test
    void streamsCollectionsAndAccumulatesOneShotIterators() {
        Buffer<String> words = CollectionConverters.asScala(List.of("elm", "willow", "oak"));
        assertEquals(List.of("ELM", "OAK"), ScalaStreamSupport.stream(words)
                .filter(word -> word.length() == 3).map(String::toUpperCase).collect(Collectors.toList()));
        assertEquals(List.of("elm", "willow", "oak"), ScalaStreamSupport.streamAccumulated(words.iterator())
                .parallel().collect(Collectors.toList()));
        assertEquals(0, ScalaStreamSupport.stream(CollectionConverters.asScala(List.<String>of())).count());
    }

    @Test
    void streamsMapEntriesKeysAndValues() {
        HashMap<String, Integer> entries = new HashMap<>();
        entries.put("pears", 4);
        entries.put("apples", 6);
        Map<String, Integer> quantities = CollectionConverters.asScala(entries);

        assertEquals(List.of("apples", "pears"), ScalaStreamSupport.streamKeys(quantities).sorted().collect(Collectors.toList()));
        assertEquals(10, ScalaStreamSupport.streamValues(quantities).mapToInt(Integer::intValue).sum());
        assertEquals(entries, ScalaStreamSupport.stream(quantities)
                .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2)));
        assertEquals(List.of("apples", "pears"), ScalaStreamSupport.streamAccumulatedKeys(quantities)
                .parallel().sorted().collect(Collectors.toList()));
        assertEquals(10, ScalaStreamSupport.streamAccumulatedValues(quantities).parallel().mapToInt(Integer::intValue).sum());
        assertEquals(10, ScalaStreamSupport.intStreamValues(quantities).sum());
        assertEquals(10, ScalaStreamSupport.intStreamAccumulatedValues(quantities).parallel().sum());
    }

    @Test
    void createsPrimitiveStreamsAndAccumulatedPrimitiveStreams() {
        Buffer<Integer> integers = CollectionConverters.asScala(List.of(2, 5, 8));
        assertEquals(15, ScalaStreamSupport.intStream(integers).sum());
        assertEquals(15, ScalaStreamSupport.intStreamAccumulated(integers.iterator()).parallel().sum());

        Buffer<Long> longs = CollectionConverters.asScala(List.of(3_000_000_000L, 4_000_000_000L));
        assertEquals(7_000_000_000L, ScalaStreamSupport.longStream(longs).sum());
        assertEquals(7_000_000_000L, ScalaStreamSupport.longStreamAccumulated(longs.iterator()).parallel().sum());

        Buffer<Double> doubles = CollectionConverters.asScala(List.of(1.25, 2.5, 4.0));
        assertEquals(7.75, ScalaStreamSupport.doubleStream(doubles).sum());
        assertEquals(7.75, ScalaStreamSupport.doubleStreamAccumulated(doubles.iterator()).parallel().sum());
    }
}
