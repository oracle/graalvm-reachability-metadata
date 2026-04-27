/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.EnumMemberValue;

import org.junit.jupiter.api.Test;

public class EnumMemberValueTest {
    @Test
    void resolvesEnumMemberValueThroughAnnotationProxy() throws Exception {
        ConstPool constPool = new ConstPool(EnumMemberValueTest.class.getName());
        Annotation annotationMetadata = new Annotation(WorkflowMarker.class.getName(), constPool);
        EnumMemberValue enumMemberValue = new EnumMemberValue(constPool);
        enumMemberValue.setType(WorkflowStage.class.getName());
        enumMemberValue.setValue(WorkflowStage.ACTIVE.name());
        annotationMetadata.addMemberValue("stage", enumMemberValue);

        ClassLoader classLoader = EnumMemberValueTest.class.getClassLoader();
        WorkflowMarker marker = (WorkflowMarker) annotationMetadata.toAnnotationType(classLoader, null);

        assertThat(marker.stage()).isSameAs(WorkflowStage.ACTIVE);
    }

    public @interface WorkflowMarker {
        WorkflowStage stage();
    }

    public enum WorkflowStage {
        ACTIVE,
        ARCHIVED
    }
}
