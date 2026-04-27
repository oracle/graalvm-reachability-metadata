/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

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
    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger();

    @Test
    void definesGeneratedClassThroughClassLoaderPublicApi() throws Exception {
        CtClass generatedClass = createGeneratedContractImplementation();
        String generatedClassName = generatedClass.getName();
        byte[] bytecode = generatedClass.toBytecode();
        generatedClass.detach();

        assertGeneratedClassDefinition(generatedClassName, bytecode);
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
