/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_classfilewriter.jboss_classfilewriter;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.ProtectionDomain;

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.junit.jupiter.api.Test;

public class DefaultClassFactoryTest {
    public interface GreetingProvider {
        String greeting();
    }

    @Test
    void definesClassThroughDefaultFactoryUsingProvidedClassLoaderAndProtectionDomain() throws ReflectiveOperationException {
        ClassLoader targetClassLoader = new TestClassLoader(getClass().getClassLoader());
        ProtectionDomain protectionDomain = getClass().getProtectionDomain();
        String className = generatedClassName("GeneratedGreetingProvider");

        ClassFile classFile = new ClassFile(
                className,
                AccessFlag.of(AccessFlag.SUPER, AccessFlag.PUBLIC),
                Object.class.getName(),
                targetClassLoader
        );
        classFile.addInterface(GreetingProvider.class.getName());

        ClassMethod constructor = classFile.addMethod(AccessFlag.PUBLIC, "<init>", "V");
        CodeAttribute constructorCode = constructor.getCodeAttribute();
        constructorCode.aload(0);
        constructorCode.invokespecial(Object.class.getName(), "<init>", "V", new String[0]);
        constructorCode.returnInstruction();

        ClassMethod greetingMethod = classFile.addMethod(AccessFlag.PUBLIC, "greeting", "Ljava/lang/String;");
        CodeAttribute greetingCode = greetingMethod.getCodeAttribute();
        greetingCode.ldc("hello from generated class");
        greetingCode.returnInstruction();

        Class<? extends GreetingProvider> definedClass = classFile
                .define(targetClassLoader, protectionDomain)
                .asSubclass(GreetingProvider.class);
        GreetingProvider instance = definedClass.getDeclaredConstructor().newInstance();

        assertThat(definedClass.getName()).isEqualTo(className);
        assertThat(definedClass.getClassLoader()).isSameAs(targetClassLoader);
        assertThat(definedClass.getProtectionDomain()).isNotNull();
        assertThat(instance.greeting()).isEqualTo("hello from generated class");
    }

    private static String generatedClassName(String simpleName) {
        return DefaultClassFactoryTest.class.getPackageName() + "." + simpleName + System.nanoTime();
    }

    private static final class TestClassLoader extends ClassLoader {
        private TestClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
