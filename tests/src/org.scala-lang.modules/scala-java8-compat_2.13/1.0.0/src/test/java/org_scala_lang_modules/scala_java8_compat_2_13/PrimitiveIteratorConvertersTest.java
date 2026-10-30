/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_java8_compat_2_13;

import org.junit.jupiter.api.Test;
import scala.compat.java8.PrimitiveIteratorConverters;
import scala.jdk.javaapi.CollectionConverters;

import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrimitiveIteratorConvertersTest {
    @Test
    void specializesJavaIterators() {
        PrimitiveIterator.OfInt integers = PrimitiveIteratorConverters.specializer_PrimitiveIteratorInt()
                .fromJava(List.<Object>of(3, 7, 11).iterator());
        assertTrue(integers.hasNext());
        assertEquals(3, integers.nextInt());
        List<Integer> remainingIntegers = new ArrayList<>();
        integers.forEachRemaining((IntConsumer) remainingIntegers::add);
        assertEquals(List.of(7, 11), remainingIntegers);
        assertFalse(integers.hasNext());

        PrimitiveIterator.OfLong longs = PrimitiveIteratorConverters.specializer_PrimitiveIteratorLong()
                .fromJava(List.<Object>of(5_000_000_000L, 6_000_000_000L).iterator());
        assertEquals(5_000_000_000L, longs.nextLong());
        List<Long> remainingLongs = new ArrayList<>();
        longs.forEachRemaining((LongConsumer) remainingLongs::add);
        assertEquals(List.of(6_000_000_000L), remainingLongs);
        assertFalse(longs.hasNext());

        PrimitiveIterator.OfDouble doubles = PrimitiveIteratorConverters.specializer_PrimitiveIteratorDouble()
                .fromJava(List.<Object>of(1.5, 2.75).iterator());
        assertEquals(1.5, doubles.nextDouble());
        List<Double> remainingDoubles = new ArrayList<>();
        doubles.forEachRemaining((DoubleConsumer) remainingDoubles::add);
        assertEquals(List.of(2.75), remainingDoubles);
        assertFalse(doubles.hasNext());
    }

    @Test
    void specializesScalaIterators() {
        PrimitiveIterator.OfInt integers = PrimitiveIteratorConverters.specializer_PrimitiveIteratorInt()
                .fromScala(CollectionConverters.asScala(List.<Object>of(4, 9)).iterator());
        assertEquals(4, integers.nextInt());
        List<Integer> remainingIntegers = new ArrayList<>();
        integers.forEachRemaining((IntConsumer) remainingIntegers::add);
        assertEquals(List.of(9), remainingIntegers);
        assertFalse(integers.hasNext());

        PrimitiveIterator.OfLong longs = PrimitiveIteratorConverters.specializer_PrimitiveIteratorLong()
                .fromScala(CollectionConverters.asScala(List.<Object>of(7_000_000_000L, 8_000_000_000L)).iterator());
        assertEquals(7_000_000_000L, longs.nextLong());
        List<Long> remainingLongs = new ArrayList<>();
        longs.forEachRemaining((LongConsumer) remainingLongs::add);
        assertEquals(List.of(8_000_000_000L), remainingLongs);
        assertFalse(longs.hasNext());

        PrimitiveIterator.OfDouble doubles = PrimitiveIteratorConverters.specializer_PrimitiveIteratorDouble()
                .fromScala(CollectionConverters.asScala(List.<Object>of(0.25, 3.5)).iterator());
        assertEquals(0.25, doubles.nextDouble());
        List<Double> remainingDoubles = new ArrayList<>();
        doubles.forEachRemaining((DoubleConsumer) remainingDoubles::add);
        assertEquals(List.of(3.5), remainingDoubles);
        assertFalse(doubles.hasNext());
    }
}
