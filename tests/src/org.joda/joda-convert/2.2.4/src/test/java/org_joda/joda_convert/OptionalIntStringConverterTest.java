/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalInt;

import org.joda.convert.StringConvert;
import org.junit.jupiter.api.Test;

public class OptionalIntStringConverterTest {
    @Test
    public void convertsPresentAndEmptyOptionalInt() {
        StringConvert convert = new StringConvert();

        assertEquals("123", convert.convertToString(OptionalInt.of(123)));
        assertEquals("", convert.convertToString(OptionalInt.empty()));
        assertEquals(OptionalInt.of(456), convert.convertFromString(OptionalInt.class, "456"));
        assertEquals(OptionalInt.empty(), convert.convertFromString(OptionalInt.class, ""));
    }
}
