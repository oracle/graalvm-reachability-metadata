/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.asm.ClassVisitor;
import org.modelmapper.internal.asm.Opcodes;
import org.modelmapper.internal.bytebuddy.asm.ClassVisitorFactory;
import org.modelmapper.internal.bytebuddy.utility.OpenedClassReader;

public class ClassVisitorFactoryInnerCreateClassVisitorFactoryTest {
    private static final String VISITED_TYPE = "org_modelmapper/modelmapper/generated/VisitedType";
    private static final String UNWRAPPED_VISITED_TYPE = "org_modelmapper/modelmapper/generated/"
        + "UnwrappedVisitedType";

    @Test
    void createsFactoryForBundledAsmClassVisitorAndDelegatesVisits() {
        ClassVisitorFactory<ClassVisitor> factory = ClassVisitorFactory.of(ClassVisitor.class);
        RecordingClassVisitor delegate = new RecordingClassVisitor();

        ClassVisitor wrapped = factory.wrap(delegate);
        wrapped.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            VISITED_TYPE,
            null,
            "java/lang/Object",
            null);
        wrapped.visitEnd();

        ClassVisitor unwrapped = factory.unwrap(wrapped);

        assertThat(factory.getType()).isSameAs(ClassVisitor.class);
        assertThat(wrapped).isNotSameAs(delegate);
        assertThat(unwrapped).isNotNull();
        assertThat(unwrapped).isNotSameAs(wrapped);
        assertThat(delegate.visitedName).isEqualTo(VISITED_TYPE);
        assertThat(delegate.visitEnded).isTrue();

        delegate.visitedName = null;
        delegate.visitEnded = false;
        unwrapped.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            UNWRAPPED_VISITED_TYPE,
            null,
            "java/lang/Object",
            null);
        unwrapped.visitEnd();

        assertThat(delegate.visitedName).isEqualTo(UNWRAPPED_VISITED_TYPE);
        assertThat(delegate.visitEnded).isTrue();
    }

    private static final class RecordingClassVisitor extends ClassVisitor {
        private String visitedName;
        private boolean visitEnded;

        private RecordingClassVisitor() {
            super(OpenedClassReader.ASM_API);
        }

        @Override
        public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
            visitedName = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitEnd() {
            visitEnded = true;
            super.visitEnd();
        }
    }
}
