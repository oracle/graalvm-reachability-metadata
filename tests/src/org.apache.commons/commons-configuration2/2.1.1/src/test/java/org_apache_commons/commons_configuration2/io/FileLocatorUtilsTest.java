/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import org.apache.commons.configuration2.io.ClasspathLocationStrategy;
import org.apache.commons.configuration2.io.FileLocator;
import org.apache.commons.configuration2.io.FileLocatorUtils;
import org.junit.jupiter.api.Test;

public class FileLocatorUtilsTest {
    private static final String MISSING_RESOURCE =
            "org_apache_commons/commons_configuration2/io/missing-file-locator-resource.properties";

    @Test
    void searchesContextClassLoaderBeforeSystemClasspath() {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(FileLocatorUtilsTest.class.getClassLoader());
        try {
            final URL url = locateClasspathResource(MISSING_RESOURCE);

            assertThat(url).isNull();
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void searchesSystemClasspathWhenContextClassLoaderIsUnavailable() {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(null);
        try {
            final URL url = locateClasspathResource(MISSING_RESOURCE);

            assertThat(url).isNull();
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    private static URL locateClasspathResource(String resourceName) {
        final FileLocator locator = FileLocatorUtils.fileLocator()
                .fileName(resourceName)
                .create();

        return new ClasspathLocationStrategy().locate(FileLocatorUtils.DEFAULT_FILE_SYSTEM, locator);
    }
}
