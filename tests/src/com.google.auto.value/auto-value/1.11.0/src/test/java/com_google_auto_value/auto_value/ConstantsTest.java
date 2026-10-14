/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import autovalue.shaded.org.objectweb.asm.ClassVisitor;
import autovalue.shaded.org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;

public class ConstantsTest {
    @Test
    void experimentalVisitorRequiresAPreviewCompiledCaller() {
        assertThatIllegalStateException()
                .isThrownBy(ConstantsExperimentalClassVisitor::new)
                .withMessageContaining("can only be used by classes compiled with --enable-preview");
    }
}

final class ConstantsExperimentalClassVisitor extends ClassVisitor {
    @SuppressWarnings("deprecation")
    ConstantsExperimentalClassVisitor() {
        super(Opcodes.ASM10_EXPERIMENTAL);
    }
}
