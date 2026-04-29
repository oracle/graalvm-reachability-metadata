/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.GenericArrayType;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.asm.Type;
import org.modelmapper.internal.util.Types;

public class TypesTest {
    @Test
    void resolvesGenericArrayRawType() {
        Class<?> rawType = Types.rawTypeFor(new StringArrayType());

        assertThat(rawType).isEqualTo(String[].class);
    }

    @Test
    void resolvesAsmArrayType() throws ClassNotFoundException {
        Class<?> resolvedType = Types.classFor(
            Type.getType("[[Ljava/lang/String;"), getClass().getClassLoader());

        assertThat(resolvedType).isEqualTo(String[][].class);
    }

    @Test
    void resolvesAsmObjectTypeWithClassLoader() throws ClassNotFoundException {
        Class<?> resolvedType = Types.classFor(
            Type.getType("Ljava/util/ArrayList;"), getClass().getClassLoader());

        assertThat(resolvedType).isEqualTo(ArrayList.class);
    }

    @Test
    void checksJavassistProxySupportForRegularClass() {
        assertThat(Types.isProxied(RegularModel.class)).isFalse();
    }

    private static final class StringArrayType implements GenericArrayType {
        @Override
        public java.lang.reflect.Type getGenericComponentType() {
            return String.class;
        }
    }

    private static final class RegularModel {
    }
}
