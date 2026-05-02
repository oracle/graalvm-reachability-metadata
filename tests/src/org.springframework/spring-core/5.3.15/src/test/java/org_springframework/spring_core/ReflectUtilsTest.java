/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

public class ReflectUtilsTest {

    private static final AtomicInteger GENERATED_CLASS_SEQUENCE = new AtomicInteger();

    @Test
    void findConstructorParsesQualifiedJavaLangAndPrimitiveArrayTypes() {
        Constructor<?> constructor = ReflectUtils.findConstructor(
                "org.springframework.core.NamedThreadLocal(java.lang.String)"
        );

        assertThat(constructor.getDeclaringClass()).isEqualTo(NamedThreadLocal.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
        assertThatThrownBy(() -> ReflectUtils.findConstructor("String(org.example.DoesNotExist)"))
                .isInstanceOf(CodeGenerationException.class)
                .hasCauseInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> ReflectUtils.findConstructor("org.springframework.core.NamedThreadLocal(int[])"))
                .isInstanceOf(CodeGenerationException.class)
                .hasCauseInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void findsMethodsAndCreatesInstancesThroughReflectUtils() throws Exception {
        Method toStringMethod = ReflectUtils.findMethod("org.springframework.core.NamedThreadLocal.toString()");
        Method inheritedCompareMethod = ReflectUtils.findDeclaredMethod(
                AnnotationAwareOrderComparator.class,
                "compare",
                new Class<?>[] {Object.class, Object.class}
        );
        NamedThreadLocal<?> threadLocal = (NamedThreadLocal<?>) ReflectUtils.newInstance(
                NamedThreadLocal.class,
                new Class<?>[] {String.class},
                new Object[] {"reflect-utils-thread-local"}
        );

        assertThat(toStringMethod.getDeclaringClass()).isEqualTo(NamedThreadLocal.class);
        assertThat(inheritedCompareMethod.getDeclaringClass()).isEqualTo(OrderComparator.class);
        assertThat(threadLocal).hasToString("reflect-utils-thread-local");
    }

    @Test
    void enumeratesDeclaredMethodsAndFindsSingleInterfaceMethod() {
        List<Method> methods = ReflectUtils.addAllMethods(AnnotationAwareOrderComparator.class, new ArrayList<>());
        Method interfaceMethod = ReflectUtils.findInterfaceMethod(Ordered.class);

        assertThat(methods).anySatisfy(method -> assertThat(method.getName()).isEqualTo("findOrder"));
        assertThat(methods).anySatisfy(method -> assertThat(method.getName()).isEqualTo("compare"));
        assertThat(interfaceMethod.getName()).isEqualTo("getOrder");
    }

    @Test
    void defineClassUsesPublicDefineClassHook() throws Exception {
        PublicDefiningClassLoader classLoader = new PublicDefiningClassLoader(ReflectUtilsTest.class.getClassLoader());

        try {
            Class<?> definedClass = defineGeneratedClass("GeneratedReflectUtilsPublic", classLoader, null);

            assertThat(definedClass.getClassLoader()).isSameAs(classLoader);
        }
        catch (CodeGenerationException ex) {
            ignoreUnsupportedDynamicClassLoading(ex);
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    @Test
    void defineClassUsesLookupWhenContextClassLoaderMatches() throws Exception {
        ClassLoader classLoader = ReflectUtilsTest.class.getClassLoader();

        try {
            Class<?> definedClass = defineGeneratedClass(
                    "GeneratedReflectUtilsLookup", classLoader, ReflectUtilsTest.class
            );

            assertThat(definedClass.getClassLoader()).isSameAs(classLoader);
        }
        catch (CodeGenerationException ex) {
            ignoreUnsupportedDynamicClassLoading(ex);
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    @Test
    void defineClassFallsBackToLookupWithDifferentTargetClassLoader() throws Exception {
        ClassLoader childClassLoader = new ClassLoader(ReflectUtilsTest.class.getClassLoader()) {
        };

        try {
            defineGeneratedClass("GeneratedReflectUtilsFallback", childClassLoader, ReflectUtilsTest.class);
        }
        catch (CodeGenerationException ex) {
            ignoreUnsupportedDynamicClassLoading(ex);
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    private static Class<?> defineGeneratedClass(String namePrefix, ClassLoader classLoader,
            Class<?> contextClass) throws Exception {

        String className = ReflectUtilsTest.class.getPackage().getName() + "." + namePrefix
                + GENERATED_CLASS_SEQUENCE.incrementAndGet();
        String initializationPropertyName = className + ".initialized";
        try {
            Class<?> definedClass = ReflectUtils.defineClass(
                    className,
                    generateClassBytes(className, initializationPropertyName),
                    classLoader,
                    null,
                    contextClass
            );

            assertThat(definedClass.getName()).isEqualTo(className);
            assertThat(System.getProperty(initializationPropertyName)).isEqualTo(className);
            return definedClass;
        }
        finally {
            System.clearProperty(initializationPropertyName);
        }
    }

    private static byte[] generateClassBytes(String className, String initializationPropertyName) {
        String internalName = className.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        writeConstructor(writer);
        writeStaticInitializer(writer, initializationPropertyName, className);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeConstructor(ClassWriter writer) {
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
    }

    private static void writeStaticInitializer(ClassWriter writer, String initializationPropertyName,
            String initializationPropertyValue) {

        MethodVisitor initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn(initializationPropertyName);
        initializer.visitLdcInsn(initializationPropertyValue);
        initializer.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false
        );
        initializer.visitInsn(Opcodes.POP);
        initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(2, 0);
        initializer.visitEnd();
    }

    private static void ignoreUnsupportedDynamicClassLoading(CodeGenerationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
            return;
        }
        throw ex;
    }

    private static void ignoreUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static final class PublicDefiningClassLoader extends ClassLoader {

        private PublicDefiningClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> publicDefineClass(String name, byte[] bytes, ProtectionDomain protectionDomain) {
            return defineClass(name, bytes, 0, bytes.length, protectionDomain);
        }
    }
}
