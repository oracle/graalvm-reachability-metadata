/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joda.convert.FromString;
import org.joda.convert.StringConvert;
import org.joda.convert.ToString;
import org.junit.jupiter.api.Test;

public class MethodFromStringConverterTest {
    @Test
    public void invokesAnnotatedStaticFactoryMethod() {
        StringConvert convert = StringConvert.create();

        MethodValue converted = convert.convertFromString(MethodValue.class, "method-value");

        assertEquals("method-value", converted.asText());
        assertEquals("method-value", convert.convertToString(converted));
    }

    public static final class MethodValue {
        private final String value;

        MethodValue(String value) {
            this.value = value;
        }

        @ToString
        public String asText() {
            return value;
        }

        @FromString
        public static MethodValue parse(String value) {
            return new MethodValue(value);
        }
    }
}
