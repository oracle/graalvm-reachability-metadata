/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.Utils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {

    @Test
    void createInstanceOfUsesContextClassLoaderAndInstantiatesLoadedType() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        TrackingClassLoader trackingClassLoader = new TrackingClassLoader(UtilsTest.class.getClassLoader());

        Thread.currentThread().setContextClassLoader(trackingClassLoader);
        try {
            Object instance = Utils.createInstanceOf(InstantiableHelper.class.getName());

            assertThat(instance).isInstanceOf(InstantiableHelper.class);
            assertThat(trackingClassLoader.requestedClassName).isEqualTo(InstantiableHelper.class.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadClassFallsBackToClassForNameWhenContextClassLoaderIsMissing() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(null);
        try {
            Class<?> loadedClass = Utils.loadClass(InstantiableHelper.class.getName());

            assertThat(loadedClass).isEqualTo(InstantiableHelper.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadClassFallsBackToClassForNameWhenContextClassLoaderRejectsTheClass() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader blockingClassLoader = new ClassLoader(UtilsTest.class.getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (InstantiableHelper.class.getName().equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };

        Thread.currentThread().setContextClassLoader(blockingClassLoader);
        try {
            Class<?> loadedClass = Utils.loadClass(InstantiableHelper.class.getName());

            assertThat(loadedClass).isEqualTo(InstantiableHelper.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public static final class InstantiableHelper {
        public InstantiableHelper() {
        }
    }

    private static final class TrackingClassLoader extends ClassLoader {
        private String requestedClassName;

        private TrackingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requestedClassName = name;
            return super.loadClass(name);
        }
    }
}
