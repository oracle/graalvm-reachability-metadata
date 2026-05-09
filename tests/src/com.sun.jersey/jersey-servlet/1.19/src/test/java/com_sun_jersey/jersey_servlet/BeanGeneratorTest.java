/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_servlet;

import com.sun.jersey.server.impl.cdi.BeanGenerator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanGeneratorTest {
    @Test
    void createBeanClassInvokesConfiguredClassDefinitionMethod() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        CapturingClassLoader capturingClassLoader = new CapturingClassLoader(previousClassLoader);
        thread.setContextClassLoader(capturingClassLoader);
        try {
            BeanGenerator generator = newBeanGenerator(
                    "com/sun/jersey/server/impl/cdi/generated/BeanGeneratorTestBean");
            replaceDefineClassMethod(generator);

            Class<?> generatedBeanClass = invokeCreateBeanClass(generator);

            assertThat(generatedBeanClass).isSameAs(GeneratedBeanSentinel.class);
            assertThat(capturingClassLoader.getClassName())
                    .startsWith("com.sun.jersey.server.impl.cdi.generated.BeanGeneratorTestBean");
            assertThat(capturingClassLoader.getBytecode())
                    .startsWith((byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE);
            assertThat(capturingClassLoader.getOffset()).isZero();
            assertThat(capturingClassLoader.getLength()).isEqualTo(capturingClassLoader.getBytecode().length);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    private BeanGenerator newBeanGenerator(String prefix) throws Exception {
        Constructor<BeanGenerator> constructor = BeanGenerator.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(prefix);
    }

    private void replaceDefineClassMethod(BeanGenerator generator) throws Exception {
        Method method = CapturingClassLoader.class.getMethod(
                "defineGeneratedClass", String.class, byte[].class, int.class, int.class);
        Field defineClassMethod = BeanGenerator.class.getDeclaredField("defineClassMethod");
        defineClassMethod.setAccessible(true);
        defineClassMethod.set(generator, method);
    }

    private Class<?> invokeCreateBeanClass(BeanGenerator generator) throws Exception {
        Method method = BeanGenerator.class.getDeclaredMethod("createBeanClass");
        method.setAccessible(true);
        return (Class<?>) method.invoke(generator);
    }

    public static final class GeneratedBeanSentinel {
    }

    public static final class CapturingClassLoader extends ClassLoader {
        private String className;
        private byte[] bytecode;
        private int offset;
        private int length;

        private CapturingClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineGeneratedClass(String className, byte[] bytecode, int offset, int length) {
            this.className = className;
            this.bytecode = bytecode;
            this.offset = offset;
            this.length = length;
            return GeneratedBeanSentinel.class;
        }

        private String getClassName() {
            return className;
        }

        private byte[] getBytecode() {
            return bytecode;
        }

        private int getOffset() {
            return offset;
        }

        private int getLength() {
            return length;
        }
    }
}
