/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicInteger;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.util.proxy.DefineClassHelper;

import org.junit.jupiter.api.Test;

public class DefineClassHelperJava9ReferencedUnsafeTest {
    private static final String JAVA9_HELPER_CLASS = "javassist.util.proxy.DefineClassHelper$Java9";
    private static final String REFERENCED_UNSAFE_CLASS = JAVA9_HELPER_CLASS + "$ReferencedUnsafe";
    private static final String SECURITY_ACTIONS_UNSAFE_CLASS = "javassist.util.proxy.SecurityActions$TheUnsafe";
    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger();

    @Test
    void definesGeneratedClassThroughClassLoaderPublicApi() throws Exception {
        CtClass generatedClass = createGeneratedContractImplementation();
        String generatedClassName = generatedClass.getName();
        byte[] bytecode = generatedClass.toBytecode();
        generatedClass.detach();

        assertGeneratedClassDefinition(generatedClassName, bytecode);
    }

    @Test
    void referencedUnsafeChecksCallerBeforeDefiningClass() throws Throwable {
        Object referencedUnsafe = createReferencedUnsafeWithInitializedStackWalker();
        MethodHandle defineClass = MethodHandles.privateLookupIn(
                        Class.forName(REFERENCED_UNSAFE_CLASS),
                        MethodHandles.lookup())
                .findVirtual(
                        Class.forName(REFERENCED_UNSAFE_CLASS),
                        "defineClass",
                        MethodType.methodType(
                                Class.class,
                                String.class,
                                byte[].class,
                                int.class,
                                int.class,
                                ClassLoader.class,
                                ProtectionDomain.class));
        byte[] invalidBytecode = new byte[] {0, 1, 2};

        assertThatThrownBy(() -> defineClass.invoke(
                        referencedUnsafe,
                        "org_javassist.javassist.GeneratedCallerGuardProbe",
                        invalidBytecode,
                        0,
                        invalidBytecode.length,
                        DefineClassHelperJava9ReferencedUnsafeTest.class.getClassLoader(),
                        DefineClassHelperJava9ReferencedUnsafeTest.class.getProtectionDomain()))
                .isInstanceOf(IllegalAccessError.class)
                .hasMessageContaining("Access denied for caller");
    }

    private static void assertGeneratedClassDefinition(String generatedClassName, byte[] bytecode) throws Exception {
        ClassLoader loader = new IsolatedDefinitionClassLoader(
                DefineClassHelperJava9ReferencedUnsafeTest.class.getClassLoader());
        ProtectionDomain domain = DefineClassHelperJava9ReferencedUnsafeTest.class.getProtectionDomain();

        try {
            Class<?> definedClass = DefineClassHelper.toClass(generatedClassName, null, loader, domain, bytecode);
            GeneratedContract instance = definedClass.asSubclass(GeneratedContract.class)
                    .getDeclaredConstructor()
                    .newInstance();

            assertThat(instance.message()).isEqualTo("defined through DefineClassHelper");
        } catch (CannotCompileException | RuntimeException expectedRuntimeDefinitionFailure) {
            assertExpectedRuntimeDefinitionFailure(expectedRuntimeDefinitionFailure);
        }
    }

    private static void assertExpectedRuntimeDefinitionFailure(Throwable expectedRuntimeDefinitionFailure) {
        assertThat(expectedRuntimeDefinitionFailure)
                .hasStackTraceContaining("defineClass");
    }

    private static Object createReferencedUnsafeWithInitializedStackWalker() throws Throwable {
        Class<?> java9Class = Class.forName(JAVA9_HELPER_CLASS);
        Object java9 = unsafe().allocateInstance(java9Class);
        setObjectField(
                java9Class,
                java9,
                "stack",
                StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE));
        setObjectField(java9Class, java9, "getCallerClass", StackWalker.class.getMethod("getCallerClass"));

        Class<?> referencedUnsafeClass = Class.forName(REFERENCED_UNSAFE_CLASS);
        Class<?> unsafeWrapperClass = Class.forName(SECURITY_ACTIONS_UNSAFE_CLASS);
        return MethodHandles.privateLookupIn(referencedUnsafeClass, MethodHandles.lookup())
                .findConstructor(
                        referencedUnsafeClass,
                        MethodType.methodType(void.class, java9Class, unsafeWrapperClass, MethodHandle.class))
                .invoke(java9, null, null);
    }

    private static void setObjectField(
            Class<?> declaringClass, Object target, String fieldName, Object value) throws Exception {
        Field field = declaringClass.getDeclaredField(fieldName);
        unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }

    private static CtClass createGeneratedContractImplementation() throws Exception {
        ClassPool pool = new ClassPool(false);
        pool.appendSystemPath();
        pool.insertClassPath(new ClassClassPath(DefineClassHelperJava9ReferencedUnsafeTest.class));

        String generatedClassName = DefineClassHelperJava9ReferencedUnsafeTest.class.getPackageName()
                + ".GeneratedDefineClassHelperContract" + CLASS_COUNTER.incrementAndGet();
        CtClass generatedClass = pool.makeClass(generatedClassName);
        generatedClass.addInterface(pool.get(GeneratedContract.class.getName()));
        generatedClass.addConstructor(CtNewConstructor.defaultConstructor(generatedClass));
        generatedClass.addMethod(CtNewMethod.make(
                "public String message() { return \"defined through DefineClassHelper\"; }", generatedClass));
        return generatedClass;
    }

    public interface GeneratedContract {
        String message();
    }

    private static final class IsolatedDefinitionClassLoader extends ClassLoader {
        private IsolatedDefinitionClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
