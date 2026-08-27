/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.transform.AbstractClassLoader;
import org.springframework.cglib.transform.ClassFilter;

/** Verifies CGLIB's public transforming class loader selection behavior. */
public class AbstractClassLoaderTest {
    private static final String ABSENT_CLASS_NAME = "org_springframework.spring_core.AbsentTransformedType";

    @Test
    void delegatesRejectedClassToParent() throws ClassNotFoundException {
        ClassLoader parent = AbstractClassLoader.class.getClassLoader();
        AbstractClassLoader classLoader = new TestClassLoader(parent, new SelectionFilter(false));

        Class<?> loadedClass = classLoader.loadClass(String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void reportsAcceptedClassWithoutClassPathResource() {
        ClassLoader parent = AbstractClassLoader.class.getClassLoader();
        AbstractClassLoader classLoader = new TestClassLoader(parent, new SelectionFilter(true));
        ClassNotFoundException failure = null;

        try {
            classLoader.loadClass(ABSENT_CLASS_NAME);
        } catch (ClassNotFoundException exception) {
            failure = exception;
        }

        assertThat(failure).hasMessage(ABSENT_CLASS_NAME);
    }

    private static final class TestClassLoader extends AbstractClassLoader {
        private TestClassLoader(ClassLoader parent, ClassFilter filter) {
            super(parent, parent, filter);
        }
    }

    private static final class SelectionFilter implements ClassFilter {
        private final boolean accepted;

        private SelectionFilter(boolean accepted) {
            this.accepted = accepted;
        }

        @Override
        public boolean accept(String name) {
            return accepted;
        }
    }
}
