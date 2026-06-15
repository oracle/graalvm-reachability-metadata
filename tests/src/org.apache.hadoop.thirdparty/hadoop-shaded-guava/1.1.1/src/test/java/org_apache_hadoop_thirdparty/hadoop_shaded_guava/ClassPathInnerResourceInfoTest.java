/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hadoop.thirdparty.com.google.common.reflect.ClassPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ClassPathInnerResourceInfoTest {
    private static final String RESOURCE_PATH =
            "org_apache_hadoop_thirdparty/hadoop_shaded_guava/classpathfixture/resource-info.txt";
    private static final String RESOURCE_CONTENT = "loaded from shaded ResourceInfo\n";

    @Test
    void urlResolvesResourceDiscoveredFromClassPath(@TempDir Path classPathRoot) throws Exception {
        writeFixtureResource(classPathRoot);

        URL[] urls = {classPathRoot.toUri().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, null)) {
            ClassPath.ResourceInfo resourceInfo = findFixtureResourceInfo(loader);

            assertThat(resourceInfo.getResourceName()).isEqualTo(RESOURCE_PATH);
            assertThat(resourceInfo.url()).isNotNull();
            assertThat(resourceInfo.asCharSource(UTF_8).read()).isEqualTo(RESOURCE_CONTENT);
            assertThat(resourceInfo.asByteSource().read()).containsExactly(RESOURCE_CONTENT.getBytes(UTF_8));
        }
    }

    private static void writeFixtureResource(Path classPathRoot) throws Exception {
        Path resourceFile = classPathRoot.resolve(RESOURCE_PATH);
        Files.createDirectories(resourceFile.getParent());
        Files.writeString(resourceFile, RESOURCE_CONTENT, UTF_8);
    }

    private static ClassPath.ResourceInfo findFixtureResourceInfo(ClassLoader loader) throws Exception {
        return ClassPath.from(loader).getResources().stream()
                .filter(resourceInfo -> resourceInfo.getResourceName().equals(RESOURCE_PATH))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find " + RESOURCE_PATH));
    }
}
