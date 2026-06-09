/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;

import org.eclipse.sisu.space.asm.ClassVisitor;
import org.eclipse.sisu.space.asm.Opcodes;
import org.junit.jupiter.api.Test;

public class ConstantsTest {
    @Test
    void rejectsExperimentalApiForClassesNotCompiledWithPreview() throws Exception {
        String visitorResourceName = NonPreviewClassVisitor.class.getName().replace('.', '/')
            + ".class";
        ClassLoader classLoader = NonPreviewClassVisitor.class.getClassLoader();
        try (InputStream bytecode = classLoader.getResourceAsStream(visitorResourceName)) {
            assertThat(bytecode).isNotNull();
        }

        assertThatThrownBy(NonPreviewClassVisitor::new)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("can only be used by classes compiled with --enable-preview");
    }

    private static final class NonPreviewClassVisitor extends ClassVisitor {
        @SuppressWarnings("deprecation")
        private NonPreviewClassVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }
}
