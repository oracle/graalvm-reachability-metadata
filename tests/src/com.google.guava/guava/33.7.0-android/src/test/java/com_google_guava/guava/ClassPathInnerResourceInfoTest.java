/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.reflect.ClassPath;
import java.net.URL;
import org.junit.jupiter.api.Test;

public class ClassPathInnerResourceInfoTest {
    private static final String RESOURCE_PATH = "com_google_guava/guava/resources-classloader.txt";

    @Test
    void urlOpensResourceDiscoveredOnApplicationClassPath() throws Exception {
        ClassLoader loader = ClassPath.class.getClassLoader();
        ClassPath.ResourceInfo resourceInfo = ClassPath.from(loader).getResources().stream()
                .filter(candidate -> candidate.getResourceName().equals(RESOURCE_PATH))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find " + RESOURCE_PATH));

        URL resourceUrl = resourceInfo.url();

        assertThat(resourceInfo.getResourceName()).isEqualTo(RESOURCE_PATH);
        assertThat(resourceUrl).isEqualTo(loader.getResource(RESOURCE_PATH));
        assertThat(resourceInfo.asCharSource(UTF_8).read()).isEqualTo("loaded by classloader\n");
    }
}
