/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Opcodes;
import org.junit.jupiter.api.Test;

public class ConstantsTest {
    @Test
    void acceptsExperimentalAsmForAPreviewCaller() {
        ExperimentalClassVisitorFixture visitor = new ExperimentalClassVisitorFixture();

        assertThat(visitor.getDelegate()).isNull();
    }

    @Test
    void rejectsExperimentalAsmForANonPreviewCaller() {
        assertThatThrownBy(ExperimentalClassVisitor::new)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can only be used by classes compiled with --enable-preview");
    }

    private static final class ExperimentalClassVisitor extends ClassVisitor {
        private ExperimentalClassVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }
}
