/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalLong;

import org.joda.convert.StringConvert;
import org.junit.jupiter.api.Test;

public class OptionalLongStringConverterTest {
    @Test
    public void convertsPresentAndEmptyOptionalLong() {
        StringConvert convert = new StringConvert();

        assertEquals("123456789", convert.convertToString(OptionalLong.of(123456789L)));
        assertEquals("", convert.convertToString(OptionalLong.empty()));
        assertEquals(OptionalLong.of(987654321L), convert.convertFromString(OptionalLong.class, "987654321"));
        assertEquals(OptionalLong.empty(), convert.convertFromString(OptionalLong.class, ""));
    }
}
