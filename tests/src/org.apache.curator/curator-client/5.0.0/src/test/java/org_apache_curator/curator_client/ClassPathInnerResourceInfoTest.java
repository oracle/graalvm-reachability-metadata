/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.curator.shaded.com.google.common.reflect.ClassPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ClassPathInnerResourceInfoTest {
    private static final String RESOURCE_NAME = "curator-classpath/resource-info.txt";
    private static final String RESOURCE_CONTENT = "resource discovered by shaded ClassPath\n";

    @Test
    void resolvesAndReadsAResourceDiscoveredFromClassPath(@TempDir Path classPathRoot) throws Exception {
        Path resource = classPathRoot.resolve(RESOURCE_NAME);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, RESOURCE_CONTENT, UTF_8);

        URL[] classPathUrls = {classPathRoot.toUri().toURL()};
        try (URLClassLoader classLoader = new URLClassLoader(classPathUrls, null)) {
            ClassPath.ResourceInfo resourceInfo = ClassPath.from(classLoader).getResources().stream()
                    .filter(candidate -> candidate.getResourceName().equals(RESOURCE_NAME))
                    .findFirst()
                    .orElseThrow();

            assertThat(resourceInfo.url()).isEqualTo(resource.toUri().toURL());
            assertThat(resourceInfo.asCharSource(UTF_8).read()).isEqualTo(RESOURCE_CONTENT);
            assertThat(resourceInfo.asByteSource().read()).containsExactly(RESOURCE_CONTENT.getBytes(UTF_8));
        }
    }
}
