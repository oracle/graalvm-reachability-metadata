/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm_debug_all;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckMethodAdapter;

import static org.assertj.core.api.Assertions.assertThatCode;

public class CheckMethodAdapterTest {
    @Test
    void acceptsControlFlowLabelAfterCheckingDebugStatus() {
        CheckMethodAdapter adapter = new CheckMethodAdapter(null);
        Label target = new Label();

        assertThatCode(() -> {
            adapter.visitCode();
            adapter.visitJumpInsn(Opcodes.GOTO, target);
            adapter.visitLabel(target);
            adapter.visitInsn(Opcodes.RETURN);
            adapter.visitMaxs(0, 0);
            adapter.visitEnd();
        }).doesNotThrowAnyException();
    }
}
