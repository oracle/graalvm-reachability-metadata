/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.eclipse.sisu.space.asm.AnnotationVisitor;
import org.eclipse.sisu.space.asm.ClassVisitor;
import org.eclipse.sisu.space.asm.FieldVisitor;
import org.eclipse.sisu.space.asm.MethodVisitor;
import org.eclipse.sisu.space.asm.Opcodes;
import org.eclipse.sisu.space.asm.RecordComponentVisitor;
import org.junit.jupiter.api.Test;

public class ConstantsTest {
    @Test
    void experimentalClassVisitorRequiresPreviewCompiledCaller() {
        assertExperimentalApiRejected(ExperimentalClassVisitor::new);
    }

    @Test
    void experimentalAnnotationVisitorRequiresPreviewCompiledCaller() {
        assertExperimentalApiRejected(ExperimentalAnnotationVisitor::new);
    }

    @Test
    void experimentalFieldVisitorRequiresPreviewCompiledCaller() {
        assertExperimentalApiRejected(ExperimentalFieldVisitor::new);
    }

    @Test
    void experimentalMethodVisitorRequiresPreviewCompiledCaller() {
        assertExperimentalApiRejected(ExperimentalMethodVisitor::new);
    }

    @Test
    void experimentalRecordComponentVisitorRequiresPreviewCompiledCaller() {
        assertExperimentalApiRejected(ExperimentalRecordComponentVisitor::new);
    }

    private static void assertExperimentalApiRejected(ThrowingCallable visitorFactory) {
        assertThatThrownBy(visitorFactory)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("can only be used by classes compiled with --enable-preview");
    }

    private static final class ExperimentalClassVisitor extends ClassVisitor {
        @SuppressWarnings("deprecation")
        private ExperimentalClassVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }

    private static final class ExperimentalAnnotationVisitor extends AnnotationVisitor {
        @SuppressWarnings("deprecation")
        private ExperimentalAnnotationVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }

    private static final class ExperimentalFieldVisitor extends FieldVisitor {
        @SuppressWarnings("deprecation")
        private ExperimentalFieldVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }

    private static final class ExperimentalMethodVisitor extends MethodVisitor {
        @SuppressWarnings("deprecation")
        private ExperimentalMethodVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }

    private static final class ExperimentalRecordComponentVisitor
        extends RecordComponentVisitor {
        @SuppressWarnings("deprecation")
        private ExperimentalRecordComponentVisitor() {
            super(Opcodes.ASM10_EXPERIMENTAL);
        }
    }
}
