/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;
import org.jvnet.tiger_types.Types;

import static org.assertj.core.api.Assertions.assertThat;

public class TypesAnonymous3Test {
    @Test
    public void erasureCreatesArrayClassForGenericArrayType() {
        final GenericArrayType stringArrayType = new SimpleGenericArrayType(String.class);

        final Class<?> erasedType = Types.erasure(stringArrayType);

        assertThat(erasedType).isEqualTo(String[].class);
        assertThat(erasedType.getComponentType()).isEqualTo(String.class);
    }

    private static final class SimpleGenericArrayType implements GenericArrayType {
        private final Type genericComponentType;

        private SimpleGenericArrayType(Type genericComponentType) {
            this.genericComponentType = genericComponentType;
        }

        @Override
        public Type getGenericComponentType() {
            return genericComponentType;
        }
    }
}
