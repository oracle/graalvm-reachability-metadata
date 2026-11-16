/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import aj.org.objectweb.asm.ClassVisitor;
import aj.org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConstantsTest {
    @Test
    void rejectsExperimentalApiForNonPreviewVisitorBytecode() {
        assertThatThrownBy(ExperimentalVisitor::new)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ASM10_EXPERIMENTAL can only be used by classes compiled with --enable-preview");
    }

    public static class ExperimentalVisitor extends ClassVisitor {
        public ExperimentalVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }
}
