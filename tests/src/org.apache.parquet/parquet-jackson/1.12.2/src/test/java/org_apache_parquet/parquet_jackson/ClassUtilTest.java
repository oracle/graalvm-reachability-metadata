/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.util.ClassUtil;

public class ClassUtilTest {
    private static final String GENERATED_TARGET_CLASS_NAME =
            "org_apache_parquet.parquet_jackson.GeneratedBrokenMethodTarget";
    private static final String GENERATED_MISSING_CLASS_NAME =
            "org_apache_parquet.parquet_jackson.GeneratedMissingDependency";

    @Test
    void createsInstancesThroughDefaultConstructors() {
        DefaultConstructorTarget instance = ClassUtil.createInstance(DefaultConstructorTarget.class, false);

        assertThat(instance.message()).isEqualTo("constructed");
    }

    @Test
    void exposesDeclaredConstructorsThroughCtorWrappers() {
        ClassUtil.Ctor[] constructors = ClassUtil.getConstructors(MultipleConstructorsTarget.class);

        assertThat(constructors)
                .extracting(ClassUtil.Ctor::getParamCount)
                .containsExactlyInAnyOrder(0, 1);
        assertThat(constructors)
                .extracting(ClassUtil.Ctor::getDeclaringClass)
                .containsOnly(MultipleConstructorsTarget.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findsTheFirstAnnotatedEnumConstant() {
        Enum<?> preferred = ClassUtil.findFirstAnnotatedEnumValue((Class) SearchMode.class, Preferred.class);

        assertThat(preferred).isSameAs(SearchMode.FAST);
    }

    @Test
    void returnsDeclaredFieldsAndMethods() {
        Field[] fields = ClassUtil.getDeclaredFields(InspectionTarget.class);
        Method[] methods = ClassUtil.getDeclaredMethods(InspectionTarget.class);

        assertThat(fields)
                .extracting(Field::getName)
                .contains("name", "priority");
        assertThat(methods)
                .extracting(Method::getName)
                .contains("describe", "reset");
    }

    @Test
    void returnsClassMethodsFromRegularClasses() {
        Method[] methods = ClassUtil.getClassMethods(InspectionTarget.class);

        assertThat(methods)
                .extracting(Method::getName)
                .contains("describe", "reset");
    }

    @Test
    void reloadsClassMethodsFromContextClassLoaderWhenMethodTypesAreMissing() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        GeneratedClassLoader brokenClassLoader = GeneratedClassLoader.withoutDependency(originalContextClassLoader);
        GeneratedClassLoader completeClassLoader = GeneratedClassLoader.withDependency(originalContextClassLoader);
        Class<?> brokenClass = Class.forName(GENERATED_TARGET_CLASS_NAME, false, brokenClassLoader);

        Thread.currentThread().setContextClassLoader(completeClassLoader);
        try {
            Method[] methods = ClassUtil.getClassMethods(brokenClass);

            assertThat(completeClassLoader.loadedClassNames()).contains(GENERATED_TARGET_CLASS_NAME);
            assertThat(methods)
                    .extracting(Method::getName)
                    .containsExactly("dependency");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    public static final class DefaultConstructorTarget {
        public DefaultConstructorTarget() {
        }

        String message() {
            return "constructed";
        }
    }

    public static final class MultipleConstructorsTarget {
        public MultipleConstructorsTarget() {
        }

        public MultipleConstructorsTarget(String ignored) {
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Preferred {
    }

    public enum SearchMode {
        SLOW,
        @Preferred
        FAST,
        COMPLETE
    }

    public static final class InspectionTarget {
        private String name = "parquet";
        private int priority = 1;

        public String describe() {
            return name + priority;
        }

        private void reset() {
            name = "";
            priority = 0;
        }
    }

    private static final class GeneratedClassLoader extends ClassLoader {
        private final boolean includeMissingDependency;
        private final List<String> loadedClassNames = new ArrayList<>();

        private GeneratedClassLoader(ClassLoader parent, boolean includeMissingDependency) {
            super(parent);
            this.includeMissingDependency = includeMissingDependency;
        }

        static GeneratedClassLoader withoutDependency(ClassLoader parent) {
            return new GeneratedClassLoader(parent, false);
        }

        static GeneratedClassLoader withDependency(ClassLoader parent) {
            return new GeneratedClassLoader(parent, true);
        }

        List<String> loadedClassNames() {
            return loadedClassNames;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (GENERATED_TARGET_CLASS_NAME.equals(name)) {
                loadedClassNames.add(name);
                byte[] classBytes = GeneratedClassFiles.targetClass();
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            if (includeMissingDependency && GENERATED_MISSING_CLASS_NAME.equals(name)) {
                loadedClassNames.add(name);
                byte[] classBytes = GeneratedClassFiles.missingDependencyClass();
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            throw new ClassNotFoundException(name);
        }
    }

    private static final class GeneratedClassFiles {
        private GeneratedClassFiles() {
        }

        static byte[] targetClass() throws ClassNotFoundException {
            try {
                String targetInternalName = GENERATED_TARGET_CLASS_NAME.replace('.', '/');
                String missingInternalName = GENERATED_MISSING_CLASS_NAME.replace('.', '/');
                return classWithDependencyMethod(targetInternalName, missingInternalName);
            } catch (IOException e) {
                ClassNotFoundException failure = new ClassNotFoundException(GENERATED_TARGET_CLASS_NAME, e);
                throw failure;
            }
        }

        static byte[] missingDependencyClass() throws ClassNotFoundException {
            try {
                String missingInternalName = GENERATED_MISSING_CLASS_NAME.replace('.', '/');
                return classWithDefaultConstructor(missingInternalName);
            } catch (IOException e) {
                ClassNotFoundException failure = new ClassNotFoundException(GENERATED_MISSING_CLASS_NAME, e);
                throw failure;
            }
        }

        private static byte[] classWithDependencyMethod(
                String internalName,
                String dependencyInternalName
        ) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeHeader(out, internalName, dependencyInternalName, 12);
            writeConstructor(out);
            out.writeShort(0x0001);
            out.writeShort(11);
            out.writeShort(12);
            out.writeShort(1);
            out.writeShort(9);
            ByteArrayOutputStream codeBytes = new ByteArrayOutputStream();
            DataOutputStream code = new DataOutputStream(codeBytes);
            code.writeShort(1);
            code.writeShort(1);
            code.writeInt(2);
            code.writeByte(0x01);
            code.writeByte(0xb0);
            code.writeShort(0);
            code.writeShort(0);
            byte[] codeAttribute = codeBytes.toByteArray();
            out.writeInt(codeAttribute.length);
            out.write(codeAttribute);
            out.writeShort(0);
            out.flush();
            return bytes.toByteArray();
        }

        private static byte[] classWithDefaultConstructor(String internalName) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeHeader(out, internalName, null, 10);
            writeConstructor(out);
            out.writeShort(0);
            out.flush();
            return bytes.toByteArray();
        }

        private static void writeHeader(
                DataOutputStream out,
                String internalName,
                String dependencyInternalName,
                int constantPoolSize
        ) throws IOException {
            out.writeInt(0xcafebabe);
            out.writeShort(0);
            out.writeShort(52);
            out.writeShort(constantPoolSize + 1);
            out.writeByte(10);
            out.writeShort(2);
            out.writeShort(3);
            out.writeByte(7);
            out.writeShort(4);
            out.writeByte(12);
            out.writeShort(5);
            out.writeShort(6);
            out.writeByte(1);
            out.writeUTF("java/lang/Object");
            out.writeByte(1);
            out.writeUTF("<init>");
            out.writeByte(1);
            out.writeUTF("()V");
            out.writeByte(7);
            out.writeShort(8);
            out.writeByte(1);
            out.writeUTF(internalName);
            out.writeByte(1);
            out.writeUTF("Code");
            out.writeByte(1);
            out.writeUTF("SourceFile");
            if (constantPoolSize > 10) {
                out.writeByte(1);
                out.writeUTF("dependency");
                out.writeByte(1);
                out.writeUTF("()L" + dependencyInternalName + ";");
            }
            out.writeShort(0x0021);
            out.writeShort(7);
            out.writeShort(2);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(constantPoolSize > 10 ? 2 : 1);
        }

        private static void writeConstructor(DataOutputStream out) throws IOException {
            out.writeShort(0x0001);
            out.writeShort(5);
            out.writeShort(6);
            out.writeShort(1);
            out.writeShort(9);
            ByteArrayOutputStream codeBytes = new ByteArrayOutputStream();
            DataOutputStream code = new DataOutputStream(codeBytes);
            code.writeShort(1);
            code.writeShort(1);
            code.writeInt(5);
            code.writeByte(0x2a);
            code.writeByte(0xb7);
            code.writeShort(1);
            code.writeByte(0xb1);
            code.writeShort(0);
            code.writeShort(0);
            byte[] codeAttribute = codeBytes.toByteArray();
            out.writeInt(codeAttribute.length);
            out.write(codeAttribute);
        }
    }
}
