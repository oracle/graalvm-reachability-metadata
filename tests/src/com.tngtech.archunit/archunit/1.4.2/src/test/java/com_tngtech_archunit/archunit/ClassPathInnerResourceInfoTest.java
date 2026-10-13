/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.reflect.ClassPath;
import com.tngtech.archunit.thirdparty.com.google.common.reflect.ClassPath.ResourceInfo;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class ClassPathInnerResourceInfoTest {
    private static final String RESOURCE_PATH =
            "com_tngtech_archunit/archunit/resources-classloader.txt";
    private static final Path TEST_RESOURCES = Path.of("build", "resources", "test");

    @Test
    void resolvesAndReadsAResourceDiscoveredFromTheApplicationClassPath() throws Exception {
        String originalClassPath = System.getProperty("java.class.path");
        System.setProperty("java.class.path", TEST_RESOURCES.toAbsolutePath().toString());
        try {
            ResourceInfo resourceInfo = ClassPath.from(ClassLoader.getSystemClassLoader())
                    .getResources().stream()
                    .filter(info -> info.getResourceName().equals(RESOURCE_PATH))
                    .findFirst()
                    .orElseThrow();

            assertThat(resourceInfo.url()).isNotNull();
            assertThat(resourceInfo.asCharSource(UTF_8).read()).isEqualTo("loaded by classloader\n");
        } finally {
            if (originalClassPath == null) {
                System.clearProperty("java.class.path");
            } else {
                System.setProperty("java.class.path", originalClassPath);
            }
        }
    }
}
