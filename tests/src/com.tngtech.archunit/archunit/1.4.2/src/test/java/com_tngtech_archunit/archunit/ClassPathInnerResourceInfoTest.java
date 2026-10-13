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
import org.junit.jupiter.api.Test;

public class ClassPathInnerResourceInfoTest {
    private static final String RESOURCE_NAME =
            "com_tngtech_archunit/archunit/resources-classloader.txt";

    @Test
    void resourceInfoResolvesAResourceDiscoveredFromTheApplicationClassPath() throws Exception {
        ClassLoader classLoader = ClassPathInnerResourceInfoTest.class.getClassLoader();
        ClassPath.ResourceInfo resourceInfo = ClassPath.from(classLoader).getResources().stream()
                .filter(info -> info.getResourceName().equals(RESOURCE_NAME))
                .findFirst()
                .orElseThrow();

        assertThat(resourceInfo.url()).isEqualTo(classLoader.getResource(RESOURCE_NAME));
        assertThat(resourceInfo.asCharSource(UTF_8).read()).isEqualTo("loaded by classloader\n");
    }
}
