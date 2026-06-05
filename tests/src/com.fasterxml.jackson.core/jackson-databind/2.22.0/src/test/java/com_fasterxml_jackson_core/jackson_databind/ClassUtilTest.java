/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import com.fasterxml.jackson.databind.util.ClassUtil;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilTest {

    @Test
    void findsFirstAnnotatedEnumValue() {
        Enum<?> value = ClassUtil.findFirstAnnotatedEnumValue(enumClass(AnnotatedDrink.class), Primary.class);

        assertThat(value).isEqualTo(AnnotatedDrink.COFFEE);
    }

    @Test
    @SuppressWarnings("deprecation")
    void exposesDeclaredFields() {
        Field[] fields = ClassUtil.getDeclaredFields(FieldTarget.class);

        assertThat(Arrays.stream(fields).map(Field::getName))
                .contains("name", "size");
    }

    @Test
    @SuppressWarnings("deprecation")
    void exposesDeclaredMethods() {
        Method[] methods = ClassUtil.getDeclaredMethods(MethodTarget.class);

        assertThat(Arrays.stream(methods).map(Method::getName))
                .contains("getName", "rename");
    }

    @Test
    void fallsBackToThreadContextClassLoaderWhenDeclaredMethodsReferenceMissingType() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        String targetClassName = MissingMethodTypeTarget.class.getName();
        String hiddenClassName = MethodDependency.class.getName();
        byte[] targetClassBytes = createTargetClassBytes(targetClassName, hiddenClassName);
        MissingTypeClassLoader missingTypeClassLoader = new MissingTypeClassLoader(
                ClassUtilTest.class.getClassLoader(), targetClassName, hiddenClassName, targetClassBytes);
        try {
            Class<?> targetClass = missingTypeClassLoader.loadClass(targetClassName);
            currentThread.setContextClassLoader(ClassUtilTest.class.getClassLoader());

            Method[] methods = ClassUtil.getClassMethods(targetClass);

            assertThat(Arrays.stream(methods).map(Method::getName))
                    .contains("dependency");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<Enum<?>> enumClass(Class<? extends Enum> enumClass) {
        return (Class<Enum<?>>) enumClass;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface Primary {
    }

    private enum AnnotatedDrink {
        TEA,

        @Primary
        COFFEE,

        WATER
    }

    public static final class FieldTarget {
        private String name;
        private int size;
    }

    public static final class MethodTarget {
        private String name = "coffee";

        public String getName() {
            return name;
        }

        public void rename(String name) {
            this.name = name;
        }
    }

    public static final class MethodDependency {
    }

    public static final class MissingMethodTypeTarget {
        public MethodDependency dependency() {
            return new MethodDependency();
        }
    }

    private static byte[] createTargetClassBytes(String targetClassName, String dependencyClassName)
            throws IOException {
        ByteArrayOutputStream classBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(classBytes)) {
            output.writeInt(0xCAFEBABE);
            output.writeShort(0);
            output.writeShort(52);
            output.writeShort(14);
            writeUtf8(output, targetClassName.replace('.', '/'));
            output.writeByte(7);
            output.writeShort(1);
            writeUtf8(output, "java/lang/Object");
            output.writeByte(7);
            output.writeShort(3);
            writeUtf8(output, "<init>");
            writeUtf8(output, "()V");
            output.writeByte(12);
            output.writeShort(5);
            output.writeShort(6);
            output.writeByte(10);
            output.writeShort(4);
            output.writeShort(7);
            writeUtf8(output, "Code");
            writeUtf8(output, "dependency");
            writeUtf8(output, "()L" + dependencyClassName.replace('.', '/') + ";");
            writeUtf8(output, "SourceFile");
            writeUtf8(output, "ClassUtilTest.java");
            output.writeShort(0x0031);
            output.writeShort(2);
            output.writeShort(4);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(2);
            writeDefaultConstructor(output);
            writeDependencyMethod(output);
            output.writeShort(1);
            output.writeShort(12);
            output.writeInt(2);
            output.writeShort(13);
        }
        return classBytes.toByteArray();
    }

    private static void writeDefaultConstructor(DataOutputStream output) throws IOException {
        output.writeShort(0x0001);
        output.writeShort(5);
        output.writeShort(6);
        output.writeShort(1);
        output.writeShort(9);
        output.writeInt(17);
        output.writeShort(1);
        output.writeShort(1);
        output.writeInt(5);
        output.writeByte(0x2A);
        output.writeByte(0xB7);
        output.writeShort(8);
        output.writeByte(0xB1);
        output.writeShort(0);
        output.writeShort(0);
    }

    private static void writeDependencyMethod(DataOutputStream output) throws IOException {
        output.writeShort(0x0001);
        output.writeShort(10);
        output.writeShort(11);
        output.writeShort(1);
        output.writeShort(9);
        output.writeInt(14);
        output.writeShort(1);
        output.writeShort(1);
        output.writeInt(2);
        output.writeByte(0x01);
        output.writeByte(0xB0);
        output.writeShort(0);
        output.writeShort(0);
    }

    private static void writeUtf8(DataOutputStream output, String value) throws IOException {
        output.writeByte(1);
        output.writeUTF(value);
    }

    private static final class MissingTypeClassLoader extends ClassLoader {
        private final String targetClassName;
        private final String hiddenClassName;
        private final byte[] targetClassBytes;

        private MissingTypeClassLoader(ClassLoader parent, String targetClassName, String hiddenClassName,
                byte[] targetClassBytes) {
            super(parent);
            this.targetClassName = targetClassName;
            this.hiddenClassName = hiddenClassName;
            this.targetClassBytes = targetClassBytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (hiddenClassName.equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                if (targetClassName.equals(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = defineClass(name, targetClassBytes, 0, targetClassBytes.length);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
                return super.loadClass(name, resolve);
            }
        }
    }
}
