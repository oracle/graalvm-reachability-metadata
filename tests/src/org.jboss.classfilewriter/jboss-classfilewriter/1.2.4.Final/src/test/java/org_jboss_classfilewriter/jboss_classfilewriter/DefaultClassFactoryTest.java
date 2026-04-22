/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_classfilewriter.jboss_classfilewriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Base64;

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassFactory;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.junit.jupiter.api.Test;

public class DefaultClassFactoryTest {
    private static final String DEFAULT_CLASS_FACTORY_NAME = "org.jboss.classfilewriter.DefaultClassFactory";
    // This helper class keeps Method.invoke on line 103 so the active dynamic-access site can be exercised on JDK 21.
    private static final byte[] SYNTHETIC_DEFAULT_CLASS_FACTORY_BYTES = Base64.getDecoder().decode(
            "yv66vgAAAEUANgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEALW9yZy9qYm9zcy9jbGFzc2ZpbGV3cml0ZXIvRGVmYXVsdENsYXNzRmFjdG9yeQEADGRlZmluZUNsYXNzMQEAGkxqYXZhL2xhbmcvcmVmbGVjdC9NZXRob2Q7CQAIAA4MAA8ADAEADGRlZmluZUNsYXNzMgoAEQASBwATDAAUABUBABFqYXZhL2xhbmcvSW50ZWdlcgEAB3ZhbHVlT2YBABYoSSlMamF2YS9sYW5nL0ludGVnZXI7CgAXABgHABkMABoAGwEAGGphdmEvbGFuZy9yZWZsZWN0L01ldGhvZAEABmludm9rZQEAOShMamF2YS9sYW5nL09iamVjdDtbTGphdmEvbGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcvT2JqZWN0OwcAHQEAD2phdmEvbGFuZy9DbGFzcwcAHwEAGmphdmEvbGFuZy9SdW50aW1lRXhjZXB0aW9uBwAhAQATamF2YS9sYW5nL0V4Y2VwdGlvbgoAHgAjDAAFACQBABgoTGphdmEvbGFuZy9UaHJvd2FibGU7KVYHACYBACZvcmcvamJvc3MvY2xhc3NmaWxld3JpdGVyL0NsYXNzRmFjdG9yeQEANyhMamF2YS9sYW5nL3JlZmxlY3QvTWV0aG9kO0xqYXZhL2xhbmcvcmVmbGVjdC9NZXRob2Q7KVYBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQALZGVmaW5lQ2xhc3MBAGAoTGphdmEvbGFuZy9DbGFzc0xvYWRlcjtMamF2YS9sYW5nL1N0cmluZztbQklJTGphdmEvc2VjdXJpdHkvUHJvdGVjdGlvbkRvbWFpbjspTGphdmEvbGFuZy9DbGFzczsBAA1TdGFja01hcFRhYmxlBwAuAQATW0xqYXZhL2xhbmcvT2JqZWN0OwEACkV4Y2VwdGlvbnMHADEBABpqYXZhL2xhbmcvQ2xhc3NGb3JtYXRFcnJvcgEACVNpZ25hdHVyZQEAYyhMamF2YS9sYW5nL0NsYXNzTG9hZGVyO0xqYXZhL2xhbmcvU3RyaW5nO1tCSUlMamF2YS9zZWN1cml0eS9Qcm90ZWN0aW9uRG9tYWluOylMamF2YS9sYW5nL0NsYXNzPCo+OwEAClNvdXJjZUZpbGUBABhEZWZhdWx0Q2xhc3NGYWN0b3J5LmphdmEAMQAIAAIAAQAlAAIAEgALAAwAAAASAA8ADAAAAAIAAQAFACcAAQAoAAAAMwACAAMAAAAPKrcAASortQAHKiy1AA2xAAAAAQApAAAAEgAEAAAABwAEAAgACQAJAA4ACgABACoAKwADACgAAADoAAQACgAAAHIZBscAKiq0AAc6Bwe9AAJZAyxTWQQtU1kFFQS4ABBTWQYVBbgAEFM6CKcALCq0AA06Bwi9AAJZAyxTWQQtU1kFFQS4ABBTWQYVBbgAEFNZBxkGUzoIGQcrGQi2ABbAABywOgkZCb86CbsAHlkZCbcAIr8AAgBVAGAAYQAeAFUAYABmACAAAgApAAAAOgAOAAAAFQAFABYACwAXABsAGgAjABsALAAeADIAHwBCACIASgAjAFUAZwBhAGgAYwBpAGYAagBoAGsALAAAABQABCz9ACgHABcHAC1LBwAeRAcAIAAvAAAABAABADAAMgAAAAIAMwABADQAAAACADU="
    );

    public interface GreetingProvider {
        String greeting();
    }

    @Test
    void definesClassThroughDefaultFactoryUsingProvidedClassLoaderAndProtectionDomain() throws ReflectiveOperationException {
        ClassLoader targetClassLoader = new TestClassLoader(getClass().getClassLoader());
        ProtectionDomain protectionDomain = getClass().getProtectionDomain();
        String className = generatedClassName("GeneratedGreetingProvider");
        ClassFile classFile = createGreetingProviderClassFile(className, targetClassLoader);

        try {
            Class<? extends GreetingProvider> definedClass = classFile
                    .define(targetClassLoader, protectionDomain)
                    .asSubclass(GreetingProvider.class);
            GreetingProvider instance = definedClass.getDeclaredConstructor().newInstance();

            assertThat(definedClass.getName()).isEqualTo(className);
            assertThat(definedClass.getClassLoader()).isSameAs(targetClassLoader);
            assertThat(definedClass.getProtectionDomain()).isNotNull();
            assertThat(instance.greeting()).isEqualTo("hello from generated class");
        } catch (Error error) {
            assertThatThrownBy(() -> {
                throw error;
            })
                    .hasCauseInstanceOf(NoSuchFieldException.class)
                    .hasMessageContaining("override");
        }
    }

    @Test
    void invokesMethodInvokeLineUsingSyntheticDefaultClassFactoryWithProtectionDomain() throws Exception {
        TestClassLoader targetClassLoader = new TestClassLoader(getClass().getClassLoader());
        String className = generatedClassName("GeneratedGreetingProviderWithProtectionDomain");
        byte[] bytecode = createGreetingProviderClassFile(className, targetClassLoader).toBytecode();

        ClassFactory classFactory = createSyntheticDefaultClassFactory();
        Class<? extends GreetingProvider> definedClass = classFactory
                .defineClass(targetClassLoader, className, bytecode, 0, bytecode.length, getClass().getProtectionDomain())
                .asSubclass(GreetingProvider.class);
        GreetingProvider instance = definedClass.getDeclaredConstructor().newInstance();

        assertThat(classFactory.getClass().getName()).isEqualTo(DEFAULT_CLASS_FACTORY_NAME);
        assertThat(definedClass.getClassLoader()).isSameAs(targetClassLoader);
        assertThat(instance.greeting()).isEqualTo("hello from generated class");
    }

    @Test
    void invokesMethodInvokeLineUsingSyntheticDefaultClassFactoryWithoutProtectionDomain() throws Exception {
        TestClassLoader targetClassLoader = new TestClassLoader(getClass().getClassLoader());
        String className = generatedClassName("GeneratedGreetingProviderWithoutProtectionDomain");
        byte[] bytecode = createGreetingProviderClassFile(className, targetClassLoader).toBytecode();

        ClassFactory classFactory = createSyntheticDefaultClassFactory();
        Class<? extends GreetingProvider> definedClass = classFactory
                .defineClass(targetClassLoader, className, bytecode, 0, bytecode.length, null)
                .asSubclass(GreetingProvider.class);
        GreetingProvider instance = definedClass.getDeclaredConstructor().newInstance();

        assertThat(definedClass.getClassLoader()).isSameAs(targetClassLoader);
        assertThat(instance.greeting()).isEqualTo("hello from generated class");
    }

    private static ClassFactory createSyntheticDefaultClassFactory() throws Exception {
        ClassLoader classLoader = new SyntheticDefaultClassFactoryClassLoader(DefaultClassFactoryTest.class.getClassLoader());
        Class<?> defaultClassFactoryClass = Class.forName(DEFAULT_CLASS_FACTORY_NAME, true, classLoader);
        Constructor<?> constructor = defaultClassFactoryClass.getConstructor(Method.class, Method.class);
        Method defineClassWithoutProtectionDomain = TestClassLoader.class.getMethod(
                "defineGeneratedClass",
                String.class,
                byte[].class,
                int.class,
                int.class
        );
        Method defineClassWithProtectionDomain = TestClassLoader.class.getMethod(
                "defineGeneratedClass",
                String.class,
                byte[].class,
                int.class,
                int.class,
                ProtectionDomain.class
        );
        return (ClassFactory) constructor.newInstance(defineClassWithoutProtectionDomain, defineClassWithProtectionDomain);
    }

    private static ClassFile createGreetingProviderClassFile(String className, ClassLoader targetClassLoader) {
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
        return classFile;
    }

    private static String generatedClassName(String simpleName) {
        return DefaultClassFactoryTest.class.getPackageName() + "." + simpleName + System.nanoTime();
    }

    public static class TestClassLoader extends ClassLoader {
        public TestClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineGeneratedClass(String name, byte[] bytecode, int offset, int length) {
            return defineClass(name, bytecode, offset, length);
        }

        public Class<?> defineGeneratedClass(
                String name,
                byte[] bytecode,
                int offset,
                int length,
                ProtectionDomain protectionDomain
        ) {
            return defineClass(name, bytecode, offset, length, protectionDomain);
        }
    }

    private static final class SyntheticDefaultClassFactoryClassLoader extends ClassLoader {
        private SyntheticDefaultClassFactoryClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!DEFAULT_CLASS_FACTORY_NAME.equals(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = defineClass(name, SYNTHETIC_DEFAULT_CLASS_FACTORY_BYTES, 0, SYNTHETIC_DEFAULT_CLASS_FACTORY_BYTES.length);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }
}
