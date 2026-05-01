/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm_debug_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

public class SimpleVerifierTest {
    @Test
    void protectedClassLoadingSupportsObjectTypes() {
        ExposedSimpleVerifier verifier = new ExposedSimpleVerifier();

        Class<?> loadedClass = verifier.loadClass(Type.getType(String.class));

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void protectedClassLoadingSupportsArrayTypeDescriptors() {
        ExposedSimpleVerifier verifier = new ExposedSimpleVerifier();

        Class<?> loadedClass = verifier.loadClass(Type.getType(String[].class));

        assertThat(loadedClass).isSameAs(String[].class);
    }

    private static final class ExposedSimpleVerifier extends SimpleVerifier {
        private Class<?> loadClass(Type type) {
            return getClass(type);
        }
    }
}
