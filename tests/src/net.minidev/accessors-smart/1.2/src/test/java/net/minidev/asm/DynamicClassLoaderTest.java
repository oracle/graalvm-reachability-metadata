/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net.minidev.asm;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicClassLoaderTest {
    private static final String GENERATED_CLASS_NAME = "net.minidev.asm.DynamicClassLoaderGeneratedBean";
    private static final String GENERATED_CLASS_INTERNAL_NAME = GENERATED_CLASS_NAME.replace('.', '/');

    @Test
    void directInstanceDefinesAndInstantiatesGeneratedClass() throws Exception {
        byte[] classBytes = generateContractImplementation();

        try {
            GeneratedContract instance = DynamicClassLoader.directInstance(
                    GeneratedContract.class, GENERATED_CLASS_NAME, classBytes);

            assertThat(instance.message()).isEqualTo("generated");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static byte[] generateContractImplementation() {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(
                Opcodes.V1_6,
                Opcodes.ACC_PUBLIC,
                GENERATED_CLASS_INTERNAL_NAME,
                null,
                "java/lang/Object",
                new String[] {Type.getInternalName(GeneratedContract.class)});

        MethodVisitor constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        MethodVisitor message = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC, "message", "()Ljava/lang/String;", null, null);
        message.visitCode();
        message.visitLdcInsn("generated");
        message.visitInsn(Opcodes.ARETURN);
        message.visitMaxs(1, 1);
        message.visitEnd();

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    public interface GeneratedContract {
        String message();
    }
}
