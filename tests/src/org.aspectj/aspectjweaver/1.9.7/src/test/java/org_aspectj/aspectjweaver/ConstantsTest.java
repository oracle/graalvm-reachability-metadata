/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import aj.org.objectweb.asm.ClassVisitor;
import aj.org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;

public class ConstantsTest extends ClassVisitor {
    private static boolean useExperimentalApi;

    public ConstantsTest() {
        super(useExperimentalApi ? Opcodes.ASM10_EXPERIMENTAL : Opcodes.ASM9);
    }

    @Test
    void experimentalApiChecksTestClassBytecodeResourceBeforeRejectingNonPreviewClass() {
        useExperimentalApi = true;
        try {
            assertThatThrownBy(ConstantsTest::new)
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            useExperimentalApi = false;
        }
    }
}
