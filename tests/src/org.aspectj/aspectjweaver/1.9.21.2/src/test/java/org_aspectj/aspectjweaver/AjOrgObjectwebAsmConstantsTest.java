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
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AjOrgObjectwebAsmConstantsTest {
    @Test
    void checksExperimentalClassVisitorCallerBytecode() {
        assertExperimentalApiRejected(ExperimentalClassApiVisitor::new);
    }

    @Test
    void checksExperimentalAnnotationVisitorCallerBytecode() {
        assertExperimentalApiRejected(ExperimentalAnnotationApiVisitor::new);
    }

    @Test
    void checksExperimentalFieldVisitorCallerBytecode() {
        assertExperimentalApiRejected(ExperimentalFieldApiVisitor::new);
    }

    @Test
    void checksExperimentalMethodVisitorCallerBytecode() {
        assertExperimentalApiRejected(ExperimentalMethodApiVisitor::new);
    }

    @Test
    void checksExperimentalModuleVisitorCallerBytecode() {
        assertExperimentalApiRejected(ExperimentalModuleApiVisitor::new);
    }

    @Test
    void checksExperimentalRecordComponentVisitorCallerBytecode() {
        assertExperimentalApiRejected(ExperimentalRecordComponentApiVisitor::new);
    }

    private static void assertExperimentalApiRejected(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(IllegalStateException.class)
                .satisfies(throwable -> assertThat(throwable.getMessage()).isIn(
                        "ASM9_EXPERIMENTAL can only be used by classes compiled with --enable-preview",
                        "Bytecode not available, can't check class version"));
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
