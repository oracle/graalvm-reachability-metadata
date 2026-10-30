/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ModuleResource;
import org.springframework.core.io.Resource;

/** Verifies module-scoped resources and relative resource paths. */
public class ModuleResourceTest {
    @Test
    void readsResourcesFromApplicationModule() throws Exception {
        ModuleResource resource = new ModuleResource(getClass().getModule(),
                "org_springframework/spring_core/class-path-resource.txt");

        try (InputStream input = resource.getInputStream()) {
            assertThat(new String(input.readAllBytes(), UTF_8)).contains("Spring classpath resource content");
        }
        Resource relative = resource.createRelative("dynamic-access.properties");
        try (InputStream input = relative.getInputStream()) {
            assertThat(new String(input.readAllBytes(), UTF_8)).contains("library=spring-core");
        }
        assertThat(resource.getFilename()).isEqualTo("class-path-resource.txt");
    }
}
