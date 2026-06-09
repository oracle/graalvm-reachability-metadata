/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.runtime.Desc;

import org.junit.jupiter.api.Test;

public class JavassistRuntimeDescTest {
    @Test
    void resolvesClassDescriptorWithClassForName() {
        boolean previousUseContextClassLoader = Desc.useContextClassLoader;
        Desc.useContextClassLoader = false;
        try {
            Class<?> resolvedType = Desc.getType("Ljava/lang/String;");

            assertThat(resolvedType).isSameAs(String.class);
        } finally {
            Desc.useContextClassLoader = previousUseContextClassLoader;
        }
    }

    @Test
    void resolvesClassNameWithContextClassLoader() {
        boolean previousUseContextClassLoader = Desc.useContextClassLoader;
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        RecordingClassLoader recordingClassLoader =
                new RecordingClassLoader(previousContextClassLoader);
        Desc.useContextClassLoader = true;
        Thread.currentThread().setContextClassLoader(recordingClassLoader);
        try {
            Class<?> resolvedClass = Desc.getClazz("java.lang.String");

            assertThat(resolvedClass).isSameAs(String.class);
            assertThat(recordingClassLoader.requestedName).isEqualTo("java.lang.String");
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            Desc.useContextClassLoader = previousUseContextClassLoader;
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private String requestedName;

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requestedName = name;
            return super.loadClass(name);
        }
    }
}
