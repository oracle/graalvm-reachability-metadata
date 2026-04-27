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
import javassist.ClassPool;
import javassist.CtClass;
import javassist.util.proxy.DefineClassHelper;

import org.junit.jupiter.api.Test;

public class DefineClassHelperJavaOtherTest {
    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger();

    @Test
    void definesClassThroughClassLoaderReflectiveFallback() throws Exception {
        CtClass generatedClass = createGeneratedClass();
        String generatedClassName = generatedClass.getName();
        byte[] bytecode = generatedClass.toBytecode();
        generatedClass.detach();

        ClassLoader loader = new IsolatedDefinitionClassLoader(DefineClassHelperJavaOtherTest.class.getClassLoader());
        ProtectionDomain domain = DefineClassHelperJavaOtherTest.class.getProtectionDomain();

        try {
            Class<?> definedClass = DefineClassHelper.toClass(generatedClassName, null, loader, domain, bytecode);

            assertThat(definedClass.getName()).isEqualTo(generatedClassName);
            assertThat(definedClass.getClassLoader()).isSameAs(loader);
        } catch (CannotCompileException | RuntimeException definitionFailure) {
            assertRuntimeFallbackReached(definitionFailure);
        }
    }

    private static CtClass createGeneratedClass() {
        ClassPool pool = new ClassPool(false);
        pool.appendSystemPath();

        String generatedClassName = DefineClassHelperJavaOtherTest.class.getPackageName()
                + ".GeneratedJavaOtherDefinition" + CLASS_COUNTER.incrementAndGet();
        return pool.makeClass(generatedClassName);
    }

    private static void assertRuntimeFallbackReached(Throwable definitionFailure) {
        assertThat(definitionFailure).hasStackTraceContaining("java.lang.reflect.Method.invoke");
    }

    private static final class IsolatedDefinitionClassLoader extends ClassLoader {
        private IsolatedDefinitionClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
