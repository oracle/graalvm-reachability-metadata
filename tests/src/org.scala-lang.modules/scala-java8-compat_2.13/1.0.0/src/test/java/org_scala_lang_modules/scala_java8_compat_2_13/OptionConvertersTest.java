/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_java8_compat_2_13;

import org.junit.jupiter.api.Test;
import scala.Option;
import scala.Some;
import scala.compat.java8.OptionConverters;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OptionConvertersTest {
    @Test
    void convertsPresentAndEmptyGenericOptions() {
        Option<String> present = OptionConverters.toScala(Optional.of("message"));
        assertEquals("message", present.get());
        assertEquals(Optional.of("message"), OptionConverters.toJava(present));

        Option<String> empty = OptionConverters.toScala(Optional.<String>empty());
        assertTrue(empty.isEmpty());
        assertEquals(Optional.empty(), OptionConverters.toJava(empty));
        assertEquals(Optional.empty(), OptionConverters.toJava(new Some<String>(null)));
    }

    @Test
    void convertsPresentPrimitiveOptions() {
        Option<Object> integer = OptionConverters.toScala(OptionalInt.of(17));
        Option<Object> longInteger = OptionConverters.toScala(OptionalLong.of(5_000_000_000L));
        Option<Object> decimal = OptionConverters.toScala(OptionalDouble.of(2.75));

        assertEquals(Optional.of(17), OptionConverters.toJava(integer));
        assertEquals(Optional.of(5_000_000_000L), OptionConverters.toJava(longInteger));
        assertEquals(Optional.of(2.75), OptionConverters.toJava(decimal));
        assertEquals(OptionalInt.of(17), OptionConverters.specializer_OptionalInt().fromScala(integer));
        assertEquals(OptionalLong.of(5_000_000_000L), OptionConverters.specializer_OptionalLong().fromScala(longInteger));
        assertEquals(OptionalDouble.of(2.75), OptionConverters.specializer_OptionalDouble().fromScala(decimal));
    }

    @Test
    void convertsEmptyPrimitiveOptions() {
        Option<Object> integer = OptionConverters.toScala(OptionalInt.empty());
        Option<Object> longInteger = OptionConverters.toScala(OptionalLong.empty());
        Option<Object> decimal = OptionConverters.toScala(OptionalDouble.empty());

        assertTrue(integer.isEmpty());
        assertTrue(longInteger.isEmpty());
        assertTrue(decimal.isEmpty());
        assertEquals(OptionalInt.empty(), OptionConverters.specializer_OptionalInt().fromScala(integer));
        assertEquals(OptionalLong.empty(), OptionConverters.specializer_OptionalLong().fromScala(longInteger));
        assertEquals(OptionalDouble.empty(), OptionConverters.specializer_OptionalDouble().fromScala(decimal));
    }
}
