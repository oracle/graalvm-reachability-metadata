/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import javassist.runtime.Desc;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DescTest {
    private static final String TARGET_CLASS_NAME = DescTest.class.getName();

    @Test
    void getClazzUsesClassForNameWhenContextClassLoaderIsDisabled() {
        boolean originalUseContextClassLoader = Desc.useContextClassLoader;
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Class<?> resolvedClass;
        try {
            Desc.useContextClassLoader = false;
            Thread.currentThread().setContextClassLoader(new EmptyClassLoader());

            resolvedClass = Desc.getClazz(TARGET_CLASS_NAME);
        } finally {
            Desc.useContextClassLoader = originalUseContextClassLoader;
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }

        assertThat(resolvedClass).isSameAs(DescTest.class);
    }

    @Test
    void getClazzDelegatesToContextClassLoaderWhenEnabled() {
        boolean originalUseContextClassLoader = Desc.useContextClassLoader;
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        RecordingClassLoader contextClassLoader = new RecordingClassLoader(originalContextClassLoader);
        Class<?> resolvedClass;
        try {
            Desc.useContextClassLoader = true;
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            resolvedClass = Desc.getClazz(TARGET_CLASS_NAME);
        } finally {
            Desc.useContextClassLoader = originalUseContextClassLoader;
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }

        assertThat(resolvedClass).isSameAs(DescTest.class);
        assertThat(contextClassLoader.loadedName).isEqualTo(TARGET_CLASS_NAME);
    }

    @Test
    void getParamsAndGetTypeResolvePrimitiveObjectAndArrayDescriptors() {
        boolean originalUseContextClassLoader = Desc.useContextClassLoader;
        Class<?>[] parameterTypes;
        Class<?> arrayType;
        try {
            Desc.useContextClassLoader = false;

            parameterTypes = Desc.getParams("(ZBCSIJFDLjava/lang/String;[[I)V");
            arrayType = Desc.getType("[Ljava/lang/String;");
        } finally {
            Desc.useContextClassLoader = originalUseContextClassLoader;
        }

        assertThat(parameterTypes).containsExactly(
                boolean.class,
                byte.class,
                char.class,
                short.class,
                int.class,
                long.class,
                float.class,
                double.class,
                String.class,
                int[][].class);
        assertThat(arrayType).isSameAs(String[].class);
    }

    private static final class EmptyClassLoader extends ClassLoader {
        private EmptyClassLoader() {
            super(null);
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private String loadedName;

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedName = name;
            return super.loadClass(name);
        }
    }
}
