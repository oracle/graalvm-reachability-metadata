/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm_debug_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckMethodAdapter;

public class CheckMethodAdapterTest {
    @Test
    void acceptsVisitedControlFlowLabel() {
        MethodNode method = new MethodNode(Opcodes.ASM5, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "branch", "()V", null, null);
        CheckMethodAdapter adapter = new CheckMethodAdapter(method);
        Label target = new Label();

        adapter.visitCode();
        adapter.visitJumpInsn(Opcodes.GOTO, target);
        adapter.visitLabel(target);
        adapter.visitInsn(Opcodes.RETURN);
        adapter.visitMaxs(0, 0);
        adapter.visitEnd();

        assertThat(method.instructions.size()).isPositive();
    }
}
