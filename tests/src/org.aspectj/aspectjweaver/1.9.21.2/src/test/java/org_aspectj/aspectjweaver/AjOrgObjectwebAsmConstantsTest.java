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

public class AjOrgObjectwebAsmConstantsTest {
    @Test
    void constructsExperimentalClassVisitorForPreviewCaller() {
        assertThat(new ExperimentalClassApiVisitor().getDelegate()).isNull();
    }

    @Test
    void constructsExperimentalAnnotationVisitorForPreviewCaller() {
        assertThat(new ExperimentalAnnotationApiVisitor().getDelegate()).isNull();
    }

    @Test
    void constructsExperimentalFieldVisitorForPreviewCaller() {
        assertThat(new ExperimentalFieldApiVisitor().getDelegate()).isNull();
    }

    @Test
    void constructsExperimentalMethodVisitorForPreviewCaller() {
        assertThat(new ExperimentalMethodApiVisitor().getDelegate()).isNull();
    }

    @Test
    void constructsExperimentalModuleVisitorForPreviewCaller() {
        assertThat(new ExperimentalModuleApiVisitor().getDelegate()).isNull();
    }

    @Test
    void constructsExperimentalRecordComponentVisitorForPreviewCaller() {
        assertThat(new ExperimentalRecordComponentApiVisitor().getDelegate()).isNull();
    }
}

final class ExperimentalClassApiVisitor extends ClassVisitor {
    ExperimentalClassApiVisitor() {
        super(((Object) Opcodes.ASM10_EXPERIMENTAL) instanceof int value ? value : 0);
    }
}

final class ExperimentalAnnotationApiVisitor extends AnnotationVisitor {
    ExperimentalAnnotationApiVisitor() {
        super(((Object) Opcodes.ASM10_EXPERIMENTAL) instanceof int value ? value : 0);
    }
}

final class ExperimentalFieldApiVisitor extends FieldVisitor {
    ExperimentalFieldApiVisitor() {
        super(((Object) Opcodes.ASM10_EXPERIMENTAL) instanceof int value ? value : 0);
    }
}

final class ExperimentalMethodApiVisitor extends MethodVisitor {
    ExperimentalMethodApiVisitor() {
        super(((Object) Opcodes.ASM10_EXPERIMENTAL) instanceof int value ? value : 0);
    }
}

final class ExperimentalModuleApiVisitor extends ModuleVisitor {
    ExperimentalModuleApiVisitor() {
        super(((Object) Opcodes.ASM10_EXPERIMENTAL) instanceof int value ? value : 0);
    }
}

final class ExperimentalRecordComponentApiVisitor extends RecordComponentVisitor {
    ExperimentalRecordComponentApiVisitor() {
        super(((Object) Opcodes.ASM10_EXPERIMENTAL) instanceof int value ? value : 0);
    }
}
