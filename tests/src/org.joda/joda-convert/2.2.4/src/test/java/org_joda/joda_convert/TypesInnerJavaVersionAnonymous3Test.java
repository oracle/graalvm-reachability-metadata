/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.ParameterizedType;

import org.joda.convert.StringConvert;
import org.junit.jupiter.api.Test;

public class TypesInnerJavaVersionAnonymous3Test {
    @Test
    public void formatsTypeArgumentsThroughCurrentJavaVersion() {
        StringConvert convert = StringConvert.create();
        ParameterizedType type = convert.convertFromString(
                ParameterizedType.class,
                "java.util.List<? extends java.lang.Number>");

        assertTrue(convert.convertToString(ParameterizedType.class, type).contains("java.lang.Number"));
    }
}
