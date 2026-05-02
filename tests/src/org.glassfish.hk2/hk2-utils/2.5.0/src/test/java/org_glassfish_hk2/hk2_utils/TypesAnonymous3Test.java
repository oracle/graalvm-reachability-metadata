/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.lang.reflect.GenericArrayType;

import org.glassfish.hk2.utilities.reflection.GenericArrayTypeImpl;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypesAnonymous3Test {
    @Test
    public void erasureCreatesArrayClassForGenericArrayType() {
        final GenericArrayType stringArrayType = new GenericArrayTypeImpl(String.class);

        final Class<?> erasedType = ReflectionHelper.getRawClass(stringArrayType);

        assertThat(erasedType).isEqualTo(String[].class);
        assertThat(erasedType.getComponentType()).isEqualTo(String.class);
    }
}
