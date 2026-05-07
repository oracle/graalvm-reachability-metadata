/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.accessors_smart;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minidev.asm.BeansAccess;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class DynamicClassLoaderTest {
    private static final String DIRECT_INSTANCE_CLASS_NAME =
            "net_minidev.accessors_smart.DynamicClassLoaderDirectInstanceSubject";

    @Test
    public void definesGeneratedAccessorAndUsesItForBeanAccess() {
        try {
            BeansAccess<MutableBean> access = BeansAccess.get(MutableBean.class);
            MutableBean bean = access.newInstance();

            access.set(bean, "name", "json-smart");
            access.set(bean, "count", Integer.valueOf(7));

            assertThat(access.get(bean, "name")).isEqualTo("json-smart");
            assertThat(access.get(bean, "count")).isEqualTo(Integer.valueOf(7));
            assertThat(bean.getName()).isEqualTo("json-smart");
            assertThat(bean.getCount()).isEqualTo(Integer.valueOf(7));
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    @Test
    public void createsInstanceFromGeneratedClassBytes() throws Exception {
        Class<?> loaderClass = Class.forName("net.minidev.asm.DynamicClassLoader");
        Method directInstance = loaderClass.getDeclaredMethod(
                "directInstance", Class.class, String.class, byte[].class);
        directInstance.setAccessible(true);

        try {
            Object instance = directInstance.invoke(
                    null,
                    DynamicClassLoaderTest.class,
                    DIRECT_INSTANCE_CLASS_NAME,
                    buildDefaultConstructibleClass(DIRECT_INSTANCE_CLASS_NAME));

            assertThat(instance.getClass().getName()).isEqualTo(DIRECT_INSTANCE_CLASS_NAME);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error) {
                rethrowIfNotNativeImageDynamicClassLoadingError(error);
                return;
            }
            throw exception;
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static byte[] buildDefaultConstructibleClass(String className) {
        ClassWriter writer = new ClassWriter(0);
        String internalName = className.replace('.', '/');
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static class MutableBean {
        private String name;
        private Integer count;

        public MutableBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }
}
