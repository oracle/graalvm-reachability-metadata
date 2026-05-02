/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import org.glassfish.hk2.utilities.reflection.GenericArrayTypeImpl;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ListerAnonymous1Test {
    @Test
    public void convertsGenericArrayTypeWithClassComponentToArrayClass() {
        final GenericArrayTypeImpl stringArrayType = new GenericArrayTypeImpl(String.class);

        final Class<?> rawClass = ReflectionHelper.getRawClass(stringArrayType);

        assertThat(rawClass).isEqualTo(String[].class);
        assertThat(rawClass.getComponentType()).isEqualTo(String.class);
    }
}
