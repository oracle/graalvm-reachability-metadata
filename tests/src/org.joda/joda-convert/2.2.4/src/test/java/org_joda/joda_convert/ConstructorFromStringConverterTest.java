/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joda.convert.StringConvert;
import org.junit.jupiter.api.Test;

public class ConstructorFromStringConverterTest {
    @Test
    public void invokesAnnotatedStringConstructor() {
        StringConvert convert = StringConvert.create();

        StringConstructorFixture converted =
                convert.convertFromString(StringConstructorFixture.class, "constructed");

        assertEquals("constructed", converted.asText());
        assertEquals("constructed", convert.convertToString(converted));
    }
}
