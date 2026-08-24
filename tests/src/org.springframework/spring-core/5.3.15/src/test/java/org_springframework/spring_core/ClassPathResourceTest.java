/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Verifies Spring's public classpath resource lookup strategies. §FS-repository-functional-spec.5.2 */
public class ClassPathResourceTest {
    private static final String RESOURCE_NAME = "class-path-resource.txt";
    private static final String RESOURCE_PATH = "org_springframework/spring_core/" + RESOURCE_NAME;
    private static final byte[] CONTENT = "Spring classpath resource content\n".getBytes(UTF_8);

    @Test
    void readsResourceRelativeToClass() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_NAME, ClassPathResourceTest.class);

        assertContent(resource);
    }

    @Test
    void readsResourceWithDefaultClassLoader() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);

        assertContent(resource);
    }

    @Test
    void readsResourceWithSystemClassLoader() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH, (Class<?>) null);

        assertContent(resource);
    }

    private static void assertContent(ClassPathResource resource) throws IOException {
        assertThat(resource.exists()).isTrue();
        try (InputStream input = resource.getInputStream()) {
            assertThat(input.readAllBytes()).isEqualTo(CONTENT);
        }
    }
}
