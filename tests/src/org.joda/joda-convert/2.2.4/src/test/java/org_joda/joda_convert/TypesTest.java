/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

import org.joda.convert.StringConvert;
import org.junit.jupiter.api.Test;

public class TypesTest {
    @Test
    public void createsArrayClassForComponentType() throws Exception {
        Method getArrayClass = Class.forName("org.joda.convert.Types")
                .getDeclaredMethod("getArrayClass", Class.class);
        getArrayClass.setAccessible(true);

        assertSame(String[].class, getArrayClass.invoke(null, String.class));
    }

    @Test
    public void formatsParsedParameterizedTypes() {
        StringConvert convert = StringConvert.create();
        ParameterizedType type = convert.convertFromString(
                ParameterizedType.class,
                "java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>");

        assertEquals(
                "java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>",
                convert.convertToString(ParameterizedType.class, type));
    }
}
