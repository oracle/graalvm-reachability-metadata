/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import aj.org.objectweb.asm.AnnotationVisitor;
import aj.org.objectweb.asm.ClassVisitor;
import aj.org.objectweb.asm.FieldVisitor;
import aj.org.objectweb.asm.MethodVisitor;
import aj.org.objectweb.asm.ModuleVisitor;
import aj.org.objectweb.asm.Opcodes;
import aj.org.objectweb.asm.RecordComponentVisitor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class AjOrgObjectwebAsmConstantsTest {
    private static final String NON_PREVIEW_BYTECODE_MESSAGE =
            "ASM9_EXPERIMENTAL can only be used by classes compiled with --enable-preview";
    private static final String CLASS_VISITOR_REJECTION_MESSAGE = readClassVisitorRejectionMessage();

    @Test
    void checksExperimentalClassVisitorCallerBytecode() {
        assertThat(CLASS_VISITOR_REJECTION_MESSAGE).isEqualTo(NON_PREVIEW_BYTECODE_MESSAGE);
    }

    @Test
    void checksExperimentalAnnotationVisitorCallerBytecode() {
        try {
            new ExperimentalAnnotationApiVisitor();
            fail("Expected experimental AnnotationVisitor API to reject non-preview bytecode");
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessage(NON_PREVIEW_BYTECODE_MESSAGE);
        }
    }

    @Test
    void checksExperimentalFieldVisitorCallerBytecode() {
        try {
            new ExperimentalFieldApiVisitor();
            fail("Expected experimental FieldVisitor API to reject non-preview bytecode");
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessage(NON_PREVIEW_BYTECODE_MESSAGE);
        }
    }

    @Test
    void checksExperimentalMethodVisitorCallerBytecode() {
        try {
            new ExperimentalMethodApiVisitor();
            fail("Expected experimental MethodVisitor API to reject non-preview bytecode");
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessage(NON_PREVIEW_BYTECODE_MESSAGE);
        }
    }

    @Test
    void checksExperimentalModuleVisitorCallerBytecode() {
        try {
            new ExperimentalModuleApiVisitor();
            fail("Expected experimental ModuleVisitor API to reject non-preview bytecode");
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessage(NON_PREVIEW_BYTECODE_MESSAGE);
        }
    }

    @Test
    void checksExperimentalRecordComponentVisitorCallerBytecode() {
        try {
            new ExperimentalRecordComponentApiVisitor();
            fail("Expected experimental RecordComponentVisitor API to reject non-preview bytecode");
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessage(NON_PREVIEW_BYTECODE_MESSAGE);
        }
    }

    private static String readClassVisitorRejectionMessage() {
        try {
            new ExperimentalClassApiVisitor();
            fail("Expected experimental ClassVisitor API to reject non-preview bytecode");
        } catch (IllegalStateException exception) {
            return exception.getMessage();
        }
        throw new AssertionError("Unreachable");
    }
}

final class ExperimentalClassApiVisitor extends ClassVisitor {
    ExperimentalClassApiVisitor() {
        super(Opcodes.ASM10_EXPERIMENTAL);
    }
}

final class ExperimentalAnnotationApiVisitor extends AnnotationVisitor {
    ExperimentalAnnotationApiVisitor() {
        super(Opcodes.ASM10_EXPERIMENTAL);
    }
}

final class ExperimentalFieldApiVisitor extends FieldVisitor {
    ExperimentalFieldApiVisitor() {
        super(Opcodes.ASM10_EXPERIMENTAL);
    }
}

final class ExperimentalMethodApiVisitor extends MethodVisitor {
    ExperimentalMethodApiVisitor() {
        super(Opcodes.ASM10_EXPERIMENTAL);
    }
}

final class ExperimentalModuleApiVisitor extends ModuleVisitor {
    ExperimentalModuleApiVisitor() {
        super(Opcodes.ASM10_EXPERIMENTAL);
    }
}

final class ExperimentalRecordComponentApiVisitor extends RecordComponentVisitor {
    ExperimentalRecordComponentApiVisitor() {
        super(Opcodes.ASM10_EXPERIMENTAL);
    }
}
