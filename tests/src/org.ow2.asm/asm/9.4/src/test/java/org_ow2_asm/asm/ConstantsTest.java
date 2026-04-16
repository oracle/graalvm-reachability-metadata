/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ConstantsTest {
    @Test
    void experimentalVisitorsLoadTheirOwnClassResourceBeforeRejectingNonPreviewBytecode() {
        assertThatIllegalStateException()
                .isThrownBy(ExperimentalClassVisitor::new)
                .withMessageContaining("--enable-preview");
    }

    @SuppressWarnings("deprecation")
    private static final class ExperimentalClassVisitor extends ClassVisitor {
        private ExperimentalClassVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }
}
