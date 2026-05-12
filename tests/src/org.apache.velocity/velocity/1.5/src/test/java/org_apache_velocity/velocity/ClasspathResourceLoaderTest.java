/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;

import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.junit.jupiter.api.Test;

public class ClasspathResourceLoaderTest {
    @Test
    void wrapsClasspathLookupFailuresAsResourceNotFoundExceptions() {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        ClassLoader throwingClassLoader = new ThrowingResourceClassLoader(originalClassLoader);
        thread.setContextClassLoader(throwingClassLoader);

        try {
            ClasspathResourceLoader loader = new ClasspathResourceLoader();

            assertThatThrownBy(() -> loader.getResourceStream("unreachable-template.vm"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("problem with template:")
                    .hasMessageContaining("unreachable-template.vm");
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    private static final class ThrowingResourceClassLoader extends ClassLoader {
        private ThrowingResourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            throw new IllegalStateException("resource lookup failed for " + name);
        }
    }
}
