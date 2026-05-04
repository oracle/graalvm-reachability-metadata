/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalDouble;

import org.joda.convert.StringConvert;
import org.junit.jupiter.api.Test;

public class OptionalDoubleStringConverterTest {
    @Test
    public void convertsPresentAndEmptyOptionalDouble() {
        StringConvert convert = new StringConvert();

        assertEquals("12.5", convert.convertToString(OptionalDouble.of(12.5d)));
        assertEquals("", convert.convertToString(OptionalDouble.empty()));
        assertEquals(OptionalDouble.of(98.25d), convert.convertFromString(OptionalDouble.class, "98.25"));
        assertEquals(OptionalDouble.empty(), convert.convertFromString(OptionalDouble.class, ""));
    }
}
