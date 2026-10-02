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
        Object api = Opcodes.ASM10_EXPERIMENTAL;
        int experimentalApi = api instanceof int value ? value : 0;
        super(experimentalApi);
    }
}

final class ExperimentalAnnotationApiVisitor extends AnnotationVisitor {
    ExperimentalAnnotationApiVisitor() {
        Object api = Opcodes.ASM10_EXPERIMENTAL;
        int experimentalApi = api instanceof int value ? value : 0;
        super(experimentalApi);
    }
}

final class ExperimentalFieldApiVisitor extends FieldVisitor {
    ExperimentalFieldApiVisitor() {
        Object api = Opcodes.ASM10_EXPERIMENTAL;
        int experimentalApi = api instanceof int value ? value : 0;
        super(experimentalApi);
    }
}

final class ExperimentalMethodApiVisitor extends MethodVisitor {
    ExperimentalMethodApiVisitor() {
        Object api = Opcodes.ASM10_EXPERIMENTAL;
        int experimentalApi = api instanceof int value ? value : 0;
        super(experimentalApi);
    }
}

final class ExperimentalModuleApiVisitor extends ModuleVisitor {
    ExperimentalModuleApiVisitor() {
        Object api = Opcodes.ASM10_EXPERIMENTAL;
        int experimentalApi = api instanceof int value ? value : 0;
        super(experimentalApi);
    }
}

final class ExperimentalRecordComponentApiVisitor extends RecordComponentVisitor {
    ExperimentalRecordComponentApiVisitor() {
        Object api = Opcodes.ASM10_EXPERIMENTAL;
        int experimentalApi = api instanceof int value ? value : 0;
        super(experimentalApi);
    }
}
