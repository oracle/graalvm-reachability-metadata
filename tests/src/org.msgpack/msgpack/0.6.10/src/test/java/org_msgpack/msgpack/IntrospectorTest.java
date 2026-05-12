/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.msgpack.template.builder.beans.BeanInfo;
import org.msgpack.template.builder.beans.EventSetDescriptor;
import org.msgpack.template.builder.beans.IntrospectionException;
import org.msgpack.template.builder.beans.Introspector;
import org.msgpack.template.builder.beans.MethodDescriptor;
import org.msgpack.template.builder.beans.PropertyDescriptor;
import org.msgpack.template.builder.beans.SimpleBeanInfo;

public class IntrospectorTest {
    private static final String CONTEXT_ONLY_BEAN_NAME = "coverage.msgpack.ContextOnlyBean";
    private static final String CONTEXT_ONLY_BEAN_INFO_NAME = CONTEXT_ONLY_BEAN_NAME + "BeanInfo";

    @Test
    void loadsExplicitBeanInfoFromBeanClassLoader() throws IntrospectionException {
        BeanInfo beanInfo = Introspector.getBeanInfo(BeanLoaderBean.class, Introspector.USE_ALL_BEANINFO);

        assertUsesEmptyExplicitBeanInfo(beanInfo, BeanLoaderBean.class);
    }

    @Test
    void loadsExplicitBeanInfoFromSystemClassLoaderWhenBeanLoaderCannotSeeIt() throws Exception {
        try {
            ByteArrayClassLoader beanLoader = new ByteArrayClassLoader(null, Map.of(
                    SystemLoaderBean.class.getName(),
                    minimalClass(SystemLoaderBean.class.getName(), Object.class.getName())));
            Class<?> beanClass = beanLoader.defineOnlyClass(SystemLoaderBean.class.getName());

            BeanInfo beanInfo = Introspector.getBeanInfo(beanClass, Introspector.USE_ALL_BEANINFO);

            assertUsesEmptyExplicitBeanInfo(beanInfo, beanClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void loadsExplicitBeanInfoFromContextClassLoaderWhenOtherLoadersCannotSeeIt() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextLoader = thread.getContextClassLoader();
        try {
            ByteArrayClassLoader beanLoader = new ByteArrayClassLoader(null, Map.of(
                    CONTEXT_ONLY_BEAN_NAME, minimalClass(CONTEXT_ONLY_BEAN_NAME, Object.class.getName())));
            ByteArrayClassLoader contextLoader = new ByteArrayClassLoader(
                    IntrospectorTest.class.getClassLoader(),
                    Map.of(CONTEXT_ONLY_BEAN_INFO_NAME,
                            minimalClass(CONTEXT_ONLY_BEAN_INFO_NAME, EmptyBeanInfo.class.getName())));
            Class<?> beanClass = beanLoader.defineOnlyClass(CONTEXT_ONLY_BEAN_NAME);
            thread.setContextClassLoader(contextLoader);

            BeanInfo beanInfo = Introspector.getBeanInfo(beanClass, Introspector.USE_ALL_BEANINFO);

            assertUsesEmptyExplicitBeanInfo(beanInfo, beanClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            thread.setContextClassLoader(originalContextLoader);
        }
    }

    private static void assertUsesEmptyExplicitBeanInfo(BeanInfo beanInfo, Class<?> beanClass) {
        assertThat(beanInfo.getBeanDescriptor().getBeanClass()).isSameAs(beanClass);
        assertThat(beanInfo.getPropertyDescriptors()).isEmpty();
        assertThat(beanInfo.getMethodDescriptors()).isEmpty();
        assertThat(beanInfo.getEventSetDescriptors()).isEmpty();
    }

    private static byte[] minimalClass(String className, String superClassName) {
        String internalName = className.replace('.', '/');
        String superInternalName = superClassName.replace('.', '/');
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(0xCAFEBABE);
            output.writeShort(0);
            output.writeShort(52);
            output.writeShort(10);
            writeUtf8(output, internalName);
            output.writeByte(7);
            output.writeShort(1);
            writeUtf8(output, superInternalName);
            output.writeByte(7);
            output.writeShort(3);
            writeUtf8(output, "<init>");
            writeUtf8(output, "()V");
            writeUtf8(output, "Code");
            output.writeByte(10);
            output.writeShort(4);
            output.writeShort(9);
            output.writeByte(12);
            output.writeShort(5);
            output.writeShort(6);
            output.writeShort(0x0021);
            output.writeShort(2);
            output.writeShort(4);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(1);
            output.writeShort(0x0001);
            output.writeShort(5);
            output.writeShort(6);
            output.writeShort(1);
            output.writeShort(7);
            output.writeInt(17);
            output.writeShort(1);
            output.writeShort(1);
            output.writeInt(5);
            output.writeByte(0x2A);
            output.writeByte(0xB7);
            output.writeShort(8);
            output.writeByte(0xB1);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("In-memory class generation should not fail", exception);
        }
    }

    private static void writeUtf8(DataOutputStream output, String value) throws IOException {
        output.writeByte(1);
        output.writeUTF(value);
    }

    public static class EmptyBeanInfo extends SimpleBeanInfo {
        @Override
        public PropertyDescriptor[] getPropertyDescriptors() {
            return new PropertyDescriptor[0];
        }

        @Override
        public MethodDescriptor[] getMethodDescriptors() {
            return new MethodDescriptor[0];
        }

        @Override
        public EventSetDescriptor[] getEventSetDescriptors() {
            return new EventSetDescriptor[0];
        }
    }

    public static class BeanLoaderBean {
        public String getName() {
            return "bean-loader";
        }
    }

    public static class BeanLoaderBeanBeanInfo extends EmptyBeanInfo {
    }

    public static class SystemLoaderBean {
        public String getName() {
            return "system-loader";
        }
    }

    public static class SystemLoaderBeanBeanInfo extends EmptyBeanInfo {
    }

    private static final class ByteArrayClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes;

        private ByteArrayClassLoader(ClassLoader parent, Map<String, byte[]> classes) {
            super(parent);
            this.classes = Collections.unmodifiableMap(classes);
        }

        private Class<?> defineOnlyClass(String name) {
            byte[] bytes = classes.get(name);
            assertThat(bytes).as("bytes for %s", name).isNotNull();
            return defineClass(name, bytes, 0, bytes.length);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
