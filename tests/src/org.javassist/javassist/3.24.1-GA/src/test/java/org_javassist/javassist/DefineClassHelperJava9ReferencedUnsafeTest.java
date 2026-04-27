/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

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
    void definesGeneratedClassThroughPublicHelperApi() throws Exception {
        CtClass generatedClass = createGeneratedContractImplementation();
        byte[] bytecode = generatedClass.toBytecode();
        generatedClass.detach();

        Class<?> definedClass = DefineClassHelper.toClass(
                DefineClassHelperJava9ReferencedUnsafeTest.class, bytecode);
        GeneratedContract instance = definedClass.asSubclass(GeneratedContract.class)
                .getDeclaredConstructor()
                .newInstance();

        assertThat(instance.message()).isEqualTo("defined through DefineClassHelper");
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
}
