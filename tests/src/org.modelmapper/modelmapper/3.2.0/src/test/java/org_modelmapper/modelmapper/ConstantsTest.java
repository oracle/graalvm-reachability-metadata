/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.asm.ClassVisitor;
import org.modelmapper.internal.asm.Opcodes;

public class ConstantsTest {
    @Test
    void experimentalApiChecksCallerBytecodeResource() {
        assertThatThrownBy(ExperimentalClassVisitor::new)
            .isInstanceOf(IllegalStateException.class);
    }

    private static final class ExperimentalClassVisitor extends ClassVisitor {
        private ExperimentalClassVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }
}
