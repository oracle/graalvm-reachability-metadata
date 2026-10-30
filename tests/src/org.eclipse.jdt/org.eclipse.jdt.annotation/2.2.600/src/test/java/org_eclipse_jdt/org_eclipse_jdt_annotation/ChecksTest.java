/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.org_eclipse_jdt_annotation;

import org.eclipse.jdt.annotation.Checks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChecksTest {
    @Test
    void validatesNonNullArguments() {
        assertDoesNotThrow(() -> Checks.assertNonNull());
        assertDoesNotThrow(() -> Checks.assertNonNull("first", "second"));
        assertDoesNotThrow(() -> Checks.assertNonNullWithMessage("required", "first", "second"));
        assertThrows(NullPointerException.class, () -> Checks.assertNonNull("first", null));
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> Checks.assertNonNullWithMessage("arguments are required", "first", null));
        assertEquals("arguments are required", error.getMessage());
    }

    @Test
    void validatesIterableElements() {
        assertDoesNotThrow(() -> Checks.assertNonNullElements(List.of()));
        assertDoesNotThrow(() -> Checks.assertNonNullElements(List.of("first", "second")));
        assertDoesNotThrow(() -> Checks.assertNonNullElements(List.of("first"), "required"));
        List<String> values = Arrays.asList("first", null, "third");
        assertThrows(NullPointerException.class, () -> Checks.assertNonNullElements(values));
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> Checks.assertNonNullElements(values, "elements are required"));
        assertEquals("elements are required", error.getMessage());
    }

    @Test
    void requiresNonNullValuesWithoutReplacingThem() {
        Object value = new Object();
        assertSame(value, Checks.requireNonNull(value));
        assertSame(value, Checks.requireNonNull(value, "required"));
        assertThrows(NullPointerException.class, () -> Checks.requireNonNull(null));
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> Checks.requireNonNull(null, "value is required"));
        assertEquals("value is required", error.getMessage());
    }

    @Test
    void requiresNonEmptyStrings() {
        String value = "configuration";
        assertSame(value, Checks.requireNonEmpty(value));
        assertSame(value, Checks.requireNonEmpty(value, "required"));
        assertEquals(" ", Checks.requireNonEmpty(" "));
        assertThrows(NullPointerException.class, () -> Checks.requireNonEmpty((String) null));
        assertThrows(IllegalArgumentException.class, () -> Checks.requireNonEmpty(""));
        NullPointerException nullError = assertThrows(NullPointerException.class,
                () -> Checks.requireNonEmpty((String) null, "name is required"));
        assertEquals("name is required", nullError.getMessage());
        IllegalArgumentException emptyError = assertThrows(IllegalArgumentException.class,
                () -> Checks.requireNonEmpty("", "name must not be empty"));
        assertEquals("name must not be empty", emptyError.getMessage());
    }

    @Test
    void requiresNonEmptyCollectionsWithoutCheckingTheirElements() {
        List<String> values = Arrays.asList("first", null);
        assertSame(values, Checks.requireNonEmpty(values));
        assertSame(values, Checks.requireNonEmpty(values, "required"));
        assertThrows(NullPointerException.class, () -> Checks.requireNonEmpty((List<String>) null));
        assertThrows(IllegalArgumentException.class, () -> Checks.requireNonEmpty(List.of()));
        NullPointerException nullError = assertThrows(NullPointerException.class,
                () -> Checks.requireNonEmpty((List<String>) null, "list is required"));
        assertEquals("list is required", nullError.getMessage());
        IllegalArgumentException emptyError = assertThrows(IllegalArgumentException.class,
                () -> Checks.requireNonEmpty(List.of(), "list must not be empty"));
        assertEquals("list must not be empty", emptyError.getMessage());
    }

    @Test
    void detectsNullValuesAndElements() {
        assertTrue(Checks.isNull(null));
        assertFalse(Checks.isNull("present"));
        assertFalse(Checks.isAnyNull());
        assertFalse(Checks.isAnyNull("first", "second"));
        assertTrue(Checks.isAnyNull("first", null, "third"));
        assertFalse(Checks.containsNull(List.of()));
        assertFalse(Checks.containsNull(List.of("first", "second")));
        assertTrue(Checks.containsNull(Arrays.asList("first", null, "third")));
    }

    @Test
    void convertsOptionalValuesToNullableValues() {
        Object value = new Object();
        assertSame(value, Checks.asNullable(Optional.of(value)));
        assertNull(Checks.asNullable(Optional.empty()));
    }

    @Test
    void selectsFallbackValuesOnlyForNullInputs() {
        Object value = new Object();
        Object fallback = new Object();
        assertSame(value, Checks.nonNullElse(value, fallback));
        assertSame(fallback, Checks.nonNullElse(null, fallback));
        AtomicInteger calls = new AtomicInteger();
        Supplier<Object> supplier = () -> {
            calls.incrementAndGet();
            return fallback;
        };
        assertSame(value, Checks.nonNullElseGet(value, supplier));
        assertEquals(0, calls.get());
        assertSame(fallback, Checks.nonNullElseGet(null, supplier));
        assertEquals(1, calls.get());
    }

    @Test
    void invokesConsumerOnlyForNonNullInputs() {
        List<String> received = new ArrayList<>();
        Checks.ifNonNull("payload", received::add);
        Checks.<String>ifNonNull(null, received::add);
        assertEquals(List.of("payload"), received);
    }

    @Test
    void appliesFunctionsOnlyForNonNullInputs() {
        AtomicInteger calls = new AtomicInteger();
        Function<String, Integer> length = value -> {
            calls.incrementAndGet();
            return value.length();
        };
        assertEquals(Integer.valueOf(7), Checks.applyIfNonNull("payload", length));
        assertNull(Checks.applyIfNonNull(null, length));
        assertEquals(Integer.valueOf(7), Checks.applyIfNonNullElse("payload", length, -1));
        assertEquals(Integer.valueOf(-1), Checks.applyIfNonNullElse(null, length, -1));
        assertEquals(2, calls.get());
    }

    @Test
    void computesFallbackOnlyWhenTheInputIsNull() {
        AtomicInteger functionCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        Function<String, Integer> length = value -> {
            functionCalls.incrementAndGet();
            return value.length();
        };
        Supplier<Integer> fallback = () -> {
            fallbackCalls.incrementAndGet();
            return -1;
        };
        assertEquals(Integer.valueOf(7), Checks.applyIfNonNullElseGet("payload", length, fallback));
        assertEquals(1, functionCalls.get());
        assertEquals(0, fallbackCalls.get());
        assertEquals(Integer.valueOf(-1), Checks.applyIfNonNullElseGet(null, length, fallback));
        assertEquals(1, functionCalls.get());
        assertEquals(1, fallbackCalls.get());
    }

    @Test
    void preservesNullResultsFromFunctions() {
        Function<String, String> function = value -> null;
        AtomicInteger fallbackCalls = new AtomicInteger();
        Supplier<String> fallback = () -> {
            fallbackCalls.incrementAndGet();
            return "fallback";
        };
        assertNull(Checks.applyIfNonNull("payload", function));
        assertNull(Checks.applyIfNonNullElse("payload", function, "fallback"));
        assertNull(Checks.applyIfNonNullElseGet("payload", function, fallback));
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    void unboxesPresentPrimitiveWrappers() {
        assertFalse(Checks.unboxElse(Boolean.FALSE, true));
        assertTrue(Checks.unboxElse(Boolean.TRUE, false));
        assertEquals((byte) 12, Checks.unboxElse(Byte.valueOf((byte) 12), (byte) 3));
        assertEquals('x', Checks.unboxElse(Character.valueOf('x'), 'y'));
        assertEquals(42, Checks.unboxElse(Integer.valueOf(42), -1));
        assertEquals(123L, Checks.unboxElse(Long.valueOf(123L), -1L));
        assertEquals((short) 15, Checks.unboxElse(Short.valueOf((short) 15), (short) 3));
        assertEquals(1.5f, Checks.unboxElse(Float.valueOf(1.5f), -1.0f));
        assertEquals(2.5d, Checks.unboxElse(Double.valueOf(2.5d), -1.0d));
    }

    @Test
    void usesPrimitiveFallbacksForNullWrappers() {
        assertTrue(Checks.unboxElse((Boolean) null, true));
        assertFalse(Checks.unboxElse((Boolean) null, false));
        assertEquals((byte) 3, Checks.unboxElse((Byte) null, (byte) 3));
        assertEquals('y', Checks.unboxElse((Character) null, 'y'));
        assertEquals(-1, Checks.unboxElse((Integer) null, -1));
        assertEquals(-1L, Checks.unboxElse((Long) null, -1L));
        assertEquals((short) 3, Checks.unboxElse((Short) null, (short) 3));
        assertEquals(-1.0f, Checks.unboxElse((Float) null, -1.0f));
        assertEquals(-1.0d, Checks.unboxElse((Double) null, -1.0d));
    }
}
