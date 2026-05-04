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

public class ReflectionStringConverterTest {
    @Test
    public void invokesAnnotatedToStringMethod() {
        StringConvert convert = StringConvert.create();
        ReflectiveValue value = ReflectiveValue.parse("reflected");

        assertEquals("reflected", convert.convertToString(value));
        assertEquals("reflected", convert.convertFromString(ReflectiveValue.class, "reflected").asText());
    }

    public static final class ReflectiveValue {
        private final String value;

        ReflectiveValue(String value) {
            this.value = value;
        }

        @ToString
        public String asText() {
            return value;
        }

        @FromString
        public static ReflectiveValue parse(String value) {
            return new ReflectiveValue(value);
        }
    }
}
