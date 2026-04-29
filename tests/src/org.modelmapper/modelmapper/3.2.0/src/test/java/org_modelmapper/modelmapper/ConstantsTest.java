/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.asm.Opcodes;
import org.modelmapper.internal.asm.tree.ClassNode;

public class ConstantsTest {
    @Test
    void experimentalApiChecksAsmTreeClassBytecodeResource() {
        assertThatThrownBy(() -> new ClassNode(Opcodes.ASM10_EXPERIMENTAL))
            .isInstanceOf(IllegalStateException.class);
    }
}
