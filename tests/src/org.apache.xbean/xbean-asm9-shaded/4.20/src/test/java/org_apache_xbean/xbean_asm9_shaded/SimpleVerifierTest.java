/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_asm9_shaded;

import org.apache.xbean.asm9.Opcodes;
import org.apache.xbean.asm9.Type;
import org.apache.xbean.asm9.tree.analysis.BasicValue;
import org.apache.xbean.asm9.tree.analysis.SimpleVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleVerifierTest {
    @Test
    void mergeLoadsObjectTypesWhenFindingCommonSuperType() {
        SimpleVerifier verifier = new SimpleVerifier();

        BasicValue mergedValue = verifier.merge(
                new BasicValue(Type.getType(String.class)),
                new BasicValue(Type.getType(Integer.class)));

        assertThat(mergedValue.getType()).isEqualTo(Type.getType(Object.class));
    }

    @Test
    void protectedClassLoadingSupportsArrayTypeDescriptors() {
        ExposedSimpleVerifier verifier = new ExposedSimpleVerifier();

        Class<?> loadedClass = verifier.loadClass(Type.getType(String[].class));

        assertThat(loadedClass).isSameAs(String[].class);
    }

    private static final class ExposedSimpleVerifier extends SimpleVerifier {
        private ExposedSimpleVerifier() {
            super(Opcodes.ASM9, null, null, null, false);
        }

        private Class<?> loadClass(Type type) {
            return getClass(type);
        }
    }
}
