/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joda.convert.FromString;
import org.joda.convert.FromStringFactory;
import org.joda.convert.StringConvert;
import org.joda.convert.ToString;
import org.junit.jupiter.api.Test;

public class AnnotationStringConverterFactoryTest {
    @Test
    public void findsAnnotatedMethodsConstructorsInterfacesAndFactories() {
        StringConvert convert = StringConvert.create();

        CharSequenceConstructorFixture fromConstructor =
                convert.convertFromString(CharSequenceConstructorFixture.class, "char-sequence");
        StringConstructorFixture stringConstructor =
                convert.convertFromString(StringConstructorFixture.class, "string");
        MethodAnnotatedValue fromMethod = convert.convertFromString(MethodAnnotatedValue.class, "method");
        InterfaceAnnotatedValue fromInterface =
                convert.convertFromString(InterfaceAnnotatedValue.class, "interface");
        FactoryAnnotatedValue fromFactory =
                convert.convertFromString(FactoryAnnotatedValue.class, "factory");

        assertEquals("char-sequence", convert.convertToString(fromConstructor));
        assertEquals("string", convert.convertToString(stringConstructor));
        assertEquals("method", convert.convertToString(fromMethod));
        assertEquals("interface", convert.convertToString(ConvertibleInterface.class, fromInterface));
        assertEquals("factory", convert.convertToString(fromFactory));
    }

    public static final class MethodAnnotatedValue {
        private final String value;

        MethodAnnotatedValue(String value) {
            this.value = value;
        }

        @ToString
        public String asText() {
            return value;
        }

        @FromString
        public static MethodAnnotatedValue parse(String value) {
            return new MethodAnnotatedValue(value);
        }
    }

    public interface ConvertibleInterface {
        @ToString
        String asText();

        @FromString
        static InterfaceAnnotatedValue parse(String value) {
            return new InterfaceAnnotatedValue(value);
        }
    }

    public static final class InterfaceAnnotatedValue implements ConvertibleInterface {
        private final String value;

        InterfaceAnnotatedValue(String value) {
            this.value = value;
        }

        @Override
        public String asText() {
            return value;
        }
    }

    @FromStringFactory(factory = Factory.class)
    public static final class FactoryAnnotatedValue {
        private final String value;

        FactoryAnnotatedValue(String value) {
            this.value = value;
        }

        @ToString
        public String asText() {
            return value;
        }
    }

    public static final class Factory {
        private Factory() {
        }

        @FromString
        public static FactoryAnnotatedValue parse(String value) {
            return new FactoryAnnotatedValue(value);
        }
    }
}
