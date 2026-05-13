/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sonatype.guice.asm.ClassWriter;
import org.sonatype.guice.asm.Label;
import org.sonatype.guice.asm.MethodVisitor;

public class ClassWriterTest {
    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_SUPER = 0x0020;
    private static final int V1_6 = 50;
    private static final int ILOAD = 21;
    private static final int IFEQ = 153;
    private static final int GOTO = 167;
    private static final int ARETURN = 176;
    private static final int INVOKESPECIAL = 183;
    private static final int NEW = 187;
    private static final int DUP = 89;

    @Test
    void computesCommonSuperclassWhileGeneratingStackFrames() {
        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(
                V1_6,
                ACC_PUBLIC | ACC_SUPER,
                "org_sonatype_sisu/sisu_inject_plexus/GeneratedClassWriterFrames",
                null,
                "java/lang/Object",
                null);

        final MethodVisitor method = writer.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "select",
                "(Z)Ljava/lang/Object;",
                null,
                null);
        final Label elseBranch = new Label();
        final Label join = new Label();
        method.visitCode();
        method.visitVarInsn(ILOAD, 0);
        method.visitJumpInsn(IFEQ, elseBranch);
        instantiate(method, internalName(FirstClassWriterType.class));
        method.visitJumpInsn(GOTO, join);
        method.visitLabel(elseBranch);
        instantiate(method, internalName(SecondClassWriterType.class));
        method.visitLabel(join);
        method.visitInsn(ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();

        assertThat(writer.toByteArray()).isNotEmpty();
    }

    private static void instantiate(final MethodVisitor method, final String internalName) {
        method.visitTypeInsn(NEW, internalName);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, internalName, "<init>", "()V");
    }

    private static String internalName(final Class<?> type) {
        return type.getName().replace('.', '/');
    }
}

class BaseClassWriterType {
}

class FirstClassWriterType extends BaseClassWriterType {
}

class SecondClassWriterType extends BaseClassWriterType {
}
