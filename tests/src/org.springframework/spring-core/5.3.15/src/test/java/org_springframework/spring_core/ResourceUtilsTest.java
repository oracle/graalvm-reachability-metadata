/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

public class ResourceUtilsTest {

    private static final String RESOURCE_PATH =
            "org_springframework/spring_core/class-path-resource-test.txt";

    private static final String CLASSPATH_RESOURCE_LOCATION =
            ResourceUtils.CLASSPATH_URL_PREFIX + RESOURCE_PATH;

    @Test
    void resolvesClasspathUrlThroughContextClassLoader() throws Exception {
        ClassLoader classLoader = ResourceUtilsTest.class.getClassLoader();

        URL url = withContextClassLoader(classLoader, () -> ResourceUtils.getURL(CLASSPATH_RESOURCE_LOCATION));

        assertThat(url).isNotNull();
        assertUrlContent(url);
    }

    @Test
    void resolvesClasspathFileThroughContextClassLoader() throws Exception {
        ClassLoader classLoader = ResourceUtilsTest.class.getClassLoader();

        assertResolvedClasspathFile(classLoader);
    }

    @Test
    void resolvesClasspathUrlWhenThreadContextClassLoaderIsUnavailable() throws Exception {
        URL url = withContextClassLoader(null, () -> ResourceUtils.getURL(CLASSPATH_RESOURCE_LOCATION));

        assertThat(url).isNotNull();
        assertUrlContent(url);
    }

    @Test
    void resolvesClasspathFileWhenThreadContextClassLoaderIsUnavailable() throws Exception {
        assertResolvedClasspathFile(null);
    }

    @Test
    void reportsMissingClasspathResourceThroughDefaultClassLoaderLookup() {
        assertThatThrownBy(() -> ResourceUtils.getURL(ResourceUtils.CLASSPATH_URL_PREFIX + RESOURCE_PATH + ".missing"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("cannot be resolved to URL because it does not exist");
    }

    private static void assertResolvedClasspathFile(ClassLoader classLoader) throws Exception {
        try {
            File file = withContextClassLoader(classLoader, () -> ResourceUtils.getFile(CLASSPATH_RESOURCE_LOCATION));

            assertThat(file).exists().isFile();
            assertThat(file).content(StandardCharsets.UTF_8).isEqualTo("spring-core class path resource test\n");
        }
        catch (FileNotFoundException ex) {
            assertThat(ex).hasMessageContaining("does not reside in the file system");
        }
    }

    private static void assertUrlContent(URL url) throws Exception {
        try (InputStream inputStream = url.openStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("spring-core class path resource test\n");
        }
    }

    private static <T> T withContextClassLoader(ClassLoader classLoader, ThrowingSupplier<T> supplier)
            throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        }
        finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    private interface ThrowingSupplier<T> {

        T get() throws Exception;
    }
}
