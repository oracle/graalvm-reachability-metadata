/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicClassLoaderTest {
    @Test
    void directlyInstantiatesGeneratedSubclass() throws Exception {
        String generatedClassName = "net_minidev.accessors_smart.GeneratedDirectInstanceBean";
        byte[] generatedClass = createSubclass(generatedClassName, DirectInstanceBase.class);

        Class<?> loaderType = Class.forName("net.minidev.asm.DynamicClassLoader");
        Method directInstance = loaderType.getDeclaredMethod(
                "directInstance",
                Class.class,
                String.class,
                byte[].class);
        directInstance.setAccessible(true);

        DirectInstanceBase instance = (DirectInstanceBase) directInstance.invoke(
                null,
                DirectInstanceBase.class,
                generatedClassName,
                generatedClass);

        assertThat(instance).isNotNull();
        assertThat(instance.getClass().getName()).isEqualTo(generatedClassName);
        assertThat(instance.getValue()).isEqualTo("generated");
    }

    private static byte[] createSubclass(String className, Class<?> parentType) {
        String internalName = className.replace('.', '/');
        String parentInternalName = Type.getInternalName(parentType);

        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, parentInternalName, null);

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parentInternalName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    public static class DirectInstanceBase {
        private final String value;

        public DirectInstanceBase() {
            this.value = "generated";
        }

        public String getValue() {
            return this.value;
        }
    }
}
