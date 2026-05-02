/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.transform.AbstractClassLoader;
import org.springframework.cglib.transform.ClassFilter;

public class AbstractClassLoaderTest {

    @Test
    void delegatesRejectedClassesToParentClassLoader() throws Exception {
        ClassLoader parent = AbstractClassLoaderTest.class.getClassLoader();
        TestAbstractClassLoader classLoader = new TestAbstractClassLoader(
                parent,
                parent,
                className -> false
        );

        Class<?> loadedClass = classLoader.loadClass(String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void resolvesAcceptedClassesFromConfiguredClassPathResources() {
        TrackingResourceClassLoader classPath = new TrackingResourceClassLoader();
        TestAbstractClassLoader classLoader = new TestAbstractClassLoader(
                AbstractClassLoaderTest.class.getClassLoader(),
                classPath,
                className -> true
        );
        String className = "org.example.missing.AbstractClassLoaderTestCandidate";

        assertThatThrownBy(() -> classLoader.loadClass(className))
                .isInstanceOf(ClassNotFoundException.class)
                .hasMessage(className);
        assertThat(classPath.requestedResourceName)
                .isEqualTo("org/example/missing/AbstractClassLoaderTestCandidate.class");
    }

    private static final class TestAbstractClassLoader extends AbstractClassLoader {

        private TestAbstractClassLoader(ClassLoader parent, ClassLoader classPath, ClassFilter filter) {
            super(parent, classPath, filter);
        }
    }

    private static final class TrackingResourceClassLoader extends ClassLoader {

        private String requestedResourceName;

        private TrackingResourceClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResourceName = name;
            return null;
        }
    }
}
