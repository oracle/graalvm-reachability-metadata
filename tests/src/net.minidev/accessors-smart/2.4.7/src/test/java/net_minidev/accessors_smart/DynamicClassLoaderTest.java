/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import net.minidev.asm.BeansAccess;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

public class DynamicClassLoaderTest {
    @Test
    void generatesAccessorClassesWhenNoPrebuiltAccessorExists() {
        BeansAccess<RuntimeGeneratedBean> access = BeansAccess.get(RuntimeGeneratedBean.class);
        RuntimeGeneratedBean bean = access.newInstance();

        access.set(bean, "count", "7");
        access.set(bean, "label", "generated");

        assertThat(access.getClass().getName()).isEqualTo(RuntimeGeneratedBean.class.getName() + "AccAccess");
        assertThat(bean.getCount()).isEqualTo(7);
        assertThat(bean.getLabel()).isEqualTo("generated");
        assertThat(access.get(bean, "count")).isEqualTo(7);
        assertThat(access.get(bean, "label")).isEqualTo("generated");
    }

    @Test
    void directlyInstantiatesGeneratedClasses() throws ReflectiveOperationException {
        String generatedClassName = DynamicClassLoaderTest.class.getPackageName() + ".GeneratedDirectInstanceBean";

        GeneratedBase generated = directInstance(
                generatedClassName,
                generateSubclassBytes(generatedClassName, "loaded through DynamicClassLoader"));

        assertThat(generated).isNotNull();
        assertThat(generated.getClass().getName()).isEqualTo(generatedClassName);
        assertThat(generated.value()).isEqualTo("loaded through DynamicClassLoader");
    }

    private static GeneratedBase directInstance(String className, byte[] bytecode) throws ReflectiveOperationException {
        Class<?> dynamicClassLoaderClass = Class.forName("net.minidev.asm.DynamicClassLoader");
        Method directInstance = dynamicClassLoaderClass.getDeclaredMethod(
                "directInstance",
                Class.class,
                String.class,
                byte[].class);
        directInstance.setAccessible(true);
        return (GeneratedBase) directInstance.invoke(null, GeneratedBase.class, className, bytecode);
    }

    private static byte[] generateSubclassBytes(String className, String value) {
        ClassWriter classWriter = new ClassWriter(0);
        String internalClassName = className.replace('.', '/');
        String superClassName = Type.getInternalName(GeneratedBase.class);

        classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, internalClassName, null, superClassName, null);

        MethodVisitor constructor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, superClassName, "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        MethodVisitor valueMethod = classWriter.visitMethod(ACC_PUBLIC, "value", "()Ljava/lang/String;", null, null);
        valueMethod.visitCode();
        valueMethod.visitLdcInsn(value);
        valueMethod.visitInsn(ARETURN);
        valueMethod.visitMaxs(1, 1);
        valueMethod.visitEnd();

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    public abstract static class GeneratedBase {
        public abstract String value();
    }

    public static final class RuntimeGeneratedBean {
        private int count;
        private String label;

        public int getCount() {
            return this.count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getLabel() {
            return this.label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
