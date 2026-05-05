/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.reflect.TypeToken;
import org.joda.convert.StringConvert;
import org.junit.jupiter.api.Test;

public class TypeTokenStringConverterTest {
    @Test
    public void convertsGuavaTypeTokenFromString() {
        StringConvert convert = new StringConvert();

        TypeToken<?> token = convert.convertFromString(
                TypeToken.class,
                "java.util.Map<java.lang.String, java.lang.Integer>");

        assertEquals(
                "java.util.Map<java.lang.String, java.lang.Integer>",
                convert.convertToString(TypeToken.class, token));
    }
}
