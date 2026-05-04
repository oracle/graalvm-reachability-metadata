/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

import org.joda.convert.StringConvert;
import org.junit.jupiter.api.Test;

public class TypeUtilsTest {
    @Test
    public void parsesSourceAndJvmArraySyntaxInParameterizedTypes() {
        StringConvert convert = StringConvert.create();

        ParameterizedType type = convert.convertFromString(
                ParameterizedType.class,
                "java.util.Map<java.lang.String[], [Ljava.lang.Integer;>");
        Type[] arguments = type.getActualTypeArguments();

        assertEquals(Map.class, type.getRawType());
        assertArrayEquals(new Type[] {String[].class, Integer[].class}, arguments);
    }
}
