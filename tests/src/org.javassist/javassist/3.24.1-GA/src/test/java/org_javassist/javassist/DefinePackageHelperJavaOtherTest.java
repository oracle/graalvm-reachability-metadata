/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;
import javassist.util.proxy.DefinePackageHelper;

import org.junit.jupiter.api.Test;

public class DefinePackageHelperJavaOtherTest {
    private static final String JAVA_OTHER_HELPER_CLASS = "javassist.util.proxy.DefinePackageHelper$JavaOther";
    private static final String SECURITY_ACTIONS_CLASS = "javassist.util.proxy.SecurityActions";
    private static final AtomicInteger PACKAGE_COUNTER = new AtomicInteger();

    @Test
    void definesPackageThroughReflectiveJavaOtherFallback() throws Throwable {
        try {
            exerciseJavaOtherDefinePackage();
        } catch (UnsupportedOperationException runtimeClassDefinitionUnavailable) {
            assertThat(runtimeClassDefinitionUnavailable).hasMessageContaining("class");
        }
    }

    private static void exerciseJavaOtherDefinePackage() throws Throwable {
        Object javaOtherHelper = createJavaOtherHelper();
        setObjectField(javaOtherHelper, "stack", createApprovingSecurityActions());
        String packageName = DefinePackageHelperJavaOtherTest.class.getPackageName()
                + ".generated.pkg" + PACKAGE_COUNTER.incrementAndGet();
        PackageCapturingClassLoader loader = new PackageCapturingClassLoader(
                DefinePackageHelperJavaOtherTest.class.getClassLoader());

        try {
            Package definedPackage = invokeJavaOtherDefinePackage(javaOtherHelper, loader, packageName);

            assertThat(definedPackage).isSameAs(loader.definedPackage(packageName));
        } catch (RuntimeException definitionFailure) {
            assertReflectiveFallbackWasReached(definitionFailure);
        }
    }

    private static Object createJavaOtherHelper() throws Throwable {
        Class<?> javaOtherClass = Class.forName(JAVA_OTHER_HELPER_CLASS);
        return MethodHandles.privateLookupIn(javaOtherClass, MethodHandles.lookup())
                .findConstructor(javaOtherClass, MethodType.methodType(void.class))
                .invoke();
    }

    private static Object createApprovingSecurityActions() throws Throwable {
        Class<?> securityActionsClass = Class.forName(SECURITY_ACTIONS_CLASS);
        CtClass generatedStack = createApprovingSecurityActionsClass();
        try {
            Class<?> generatedClass = MethodHandles.privateLookupIn(securityActionsClass, MethodHandles.lookup())
                    .defineClass(generatedStack.toBytecode());
            return generatedClass.getDeclaredConstructor().newInstance();
        } finally {
            generatedStack.detach();
        }
    }

    private static CtClass createApprovingSecurityActionsClass() throws Exception {
        ClassPool pool = new ClassPool(false);
        pool.appendSystemPath();
        pool.insertClassPath(new LoaderClassPath(DefinePackageHelper.class.getClassLoader()));

        String generatedClassName = "javassist.util.proxy.GeneratedDefinePackageHelperJavaOtherStack"
                + PACKAGE_COUNTER.incrementAndGet();
        CtClass generatedStack = pool.makeClass(generatedClassName);
        generatedStack.setSuperclass(pool.get(SECURITY_ACTIONS_CLASS));
        generatedStack.addConstructor(CtNewConstructor.defaultConstructor(generatedStack));
        generatedStack.addMethod(CtNewMethod.make(
                "public Class getCallerClass() { return javassist.util.proxy.DefinePackageHelper.class; }",
                generatedStack));
        return generatedStack;
    }

    private static Package invokeJavaOtherDefinePackage(Object javaOtherHelper, ClassLoader loader, String packageName)
            throws Throwable {
        Class<?> javaOtherClass = Class.forName(JAVA_OTHER_HELPER_CLASS);
        MethodHandle definePackage = MethodHandles.privateLookupIn(javaOtherClass, MethodHandles.lookup())
                .findVirtual(
                        javaOtherClass,
                        "definePackage",
                        MethodType.methodType(
                                Package.class,
                                ClassLoader.class,
                                String.class,
                                String.class,
                                String.class,
                                String.class,
                                String.class,
                                String.class,
                                String.class,
                                URL.class));
        return (Package) definePackage.invoke(
                javaOtherHelper, loader, packageName, null, null, null, null, null, null, null);
    }

    private static void setObjectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
    }

    private static void assertReflectiveFallbackWasReached(Throwable definitionFailure) {
        assertThat(definitionFailure).hasStackTraceContaining("java.lang.reflect.Method.invoke");
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }

    private static final class PackageCapturingClassLoader extends ClassLoader {
        private PackageCapturingClassLoader(ClassLoader parent) {
            super(parent);
        }

        private Package definedPackage(String name) {
            return getDefinedPackage(name);
        }
    }
}
