/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

public class ClassPathResourceTest {

    private static final String PACKAGE_RESOURCE_PATH = "class-path-resource-test.txt";

    private static final String ABSOLUTE_RESOURCE_PATH =
            "org_springframework/spring_core/" + PACKAGE_RESOURCE_PATH;

    @Test
    void resolvesResourceThroughOwningClass() throws IOException {
        ClassPathResource resource = new ClassPathResource(PACKAGE_RESOURCE_PATH, ClassPathResourceTest.class);

        assertReadableResource(resource);
    }

    @Test
    void resolvesResourceThroughExplicitClassLoader() throws IOException {
        ClassLoader classLoader = ClassPathResourceTest.class.getClassLoader();
        ClassPathResource resource = new ClassPathResource(ABSOLUTE_RESOURCE_PATH, classLoader);

        assertReadableResource(resource);
    }

    @Test
    void reportsMissingResourceThroughSystemClassLoaderFallback() {
        ClassPathResource resource = new SystemLookupClassPathResource(
                "org_springframework/spring_core/missing-class-path-resource-test.txt"
        );

        assertThat(resource.exists()).isFalse();
        assertThatThrownBy(resource::getInputStream)
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("does not exist");
    }

    private static void assertReadableResource(ClassPathResource resource) throws IOException {
        assertThat(resource.exists()).isTrue();
        assertThat(resource.getURL()).isNotNull();
        try (InputStream inputStream = resource.getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("spring-core class path resource test\n");
        }
    }

    private static final class SystemLookupClassPathResource extends ClassPathResource {

        @SuppressWarnings("deprecation")
        private SystemLookupClassPathResource(String path) {
            super(path, null, null);
        }
    }
}
