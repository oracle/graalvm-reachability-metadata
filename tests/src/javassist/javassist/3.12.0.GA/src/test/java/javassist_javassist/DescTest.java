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
    @Test
    void resolvesClassNamesWithClassForNameByDefault() {
        Desc.useContextClassLoader = false;

        Class<?> resolvedClass = Desc.getClazz(String.class.getName());
        Class<?> resolvedArrayType = Desc.getType("[Ljava/lang/String;");
        Class<?>[] resolvedParameterTypes = Desc.getParams("(Ljava/lang/String;I)V");

        assertThat(resolvedClass).isSameAs(String.class);
        assertThat(resolvedArrayType).isSameAs(String[].class);
        assertThat(resolvedParameterTypes).containsExactly(String.class, int.class);
    }

    @Test
    void resolvesClassNamesWithContextClassLoaderWhenRequested() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        RecordingClassLoader recordingClassLoader = new RecordingClassLoader(originalContextClassLoader);
        boolean originalUseContextClassLoader = Desc.useContextClassLoader;

        try {
            Desc.useContextClassLoader = true;
            currentThread.setContextClassLoader(recordingClassLoader);

            Class<?> resolvedClass = Desc.getClazz(String.class.getName());

            assertThat(resolvedClass).isSameAs(String.class);
            assertThat(recordingClassLoader.loadedClassNames()).contains(String.class.getName());
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
            Desc.useContextClassLoader = originalUseContextClassLoader;
        }
    }

    private static class RecordingClassLoader extends ClassLoader {
        private final StringBuilder loadedClassNames = new StringBuilder();

        RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (loadedClassNames.length() > 0) {
                loadedClassNames.append(',');
            }
            loadedClassNames.append(name);
            return super.loadClass(name);
        }

        String loadedClassNames() {
            return loadedClassNames.toString();
        }
    }
}
