/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_asm9_shaded;

import org.apache.xbean.asm9.ClassVisitor;
import org.apache.xbean.asm9.Opcodes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConstantsTest {
    @Test
    void experimentalApiChecksCallerBytecodeThroughClassLoaderResource() {
        assertThatThrownBy(ExperimentalClassVisitor::new).isInstanceOf(IllegalStateException.class);
    }

    private static final class ExperimentalClassVisitor extends ClassVisitor {
        private ExperimentalClassVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }
}
