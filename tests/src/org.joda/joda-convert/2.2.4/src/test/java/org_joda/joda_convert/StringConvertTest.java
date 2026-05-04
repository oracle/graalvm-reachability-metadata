/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.joda.convert.RenameHandler;
import org.joda.convert.StringConvert;
import org.junit.jupiter.api.Test;

public class StringConvertTest {
    @Test
    public void registersMethodsAndConstructorsByName() {
        StringConvert convert = new StringConvert();
        convert.registerMethods(MethodValue.class, "asText", "parse");
        convert.registerMethods(CharSequenceMethodValue.class, "asText", "parse");
        convert.registerMethodConstructor(File.class, "getPath");
        convert.registerMethodConstructor(CharSequenceConstructorFixture.class, "asText");

        assertEquals("alpha", convert.convertToString(new MethodValue("alpha")));
        assertEquals("beta", convert.convertFromString(MethodValue.class, "beta").asText());
        assertEquals("gamma", convert.convertFromString(CharSequenceMethodValue.class, "gamma").asText());
        assertEquals("build.gradle", convert.convertFromString(File.class, "build.gradle").getPath());
        assertEquals(
                "delta",
                convert.convertFromString(CharSequenceConstructorFixture.class, "delta").asText());
    }

    @Test
    public void convertsJava8OptionalTypesRegisteredDuringConstruction() {
        StringConvert convert = new StringConvert();

        assertEquals("12", convert.convertToString(OptionalInt.of(12)));
        assertEquals("", convert.convertToString(OptionalInt.empty()));
        assertEquals(OptionalInt.of(34), convert.convertFromString(OptionalInt.class, "34"));
        assertEquals(OptionalInt.empty(), convert.convertFromString(OptionalInt.class, ""));

        assertEquals("56", convert.convertToString(OptionalLong.of(56L)));
        assertEquals(OptionalLong.of(78L), convert.convertFromString(OptionalLong.class, "78"));

        assertEquals("1.25", convert.convertToString(OptionalDouble.of(1.25d)));
        assertEquals(OptionalDouble.of(2.5d), convert.convertFromString(OptionalDouble.class, "2.5"));
    }

    @Test
    public void loadsTypesWithoutAContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            assertSame(String.class, RenameHandler.create(false).lookupType("java.lang.String"));
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }

    public static final class MethodValue {
        private final String value;

        MethodValue(String value) {
            this.value = value;
        }

        public String asText() {
            return value;
        }

        public static MethodValue parse(String value) {
            return new MethodValue(value);
        }
    }

    public static final class CharSequenceMethodValue {
        private final String value;

        CharSequenceMethodValue(String value) {
            this.value = value;
        }

        public String asText() {
            return value;
        }

        public static CharSequenceMethodValue parse(CharSequence value) {
            return new CharSequenceMethodValue(value.toString());
        }
    }
}
