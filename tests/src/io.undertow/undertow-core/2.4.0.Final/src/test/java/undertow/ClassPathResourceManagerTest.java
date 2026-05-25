/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.Resource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathResourceManagerTest {

    @Test
    void resolvesResourceFromClassPathWithConfiguredPrefix() throws Exception {
        ClassPathResourceManager resourceManager = new ClassPathResourceManager(classLoader(), "io/undertow");

        Resource resource = resourceManager.getResource("/version.properties");

        assertThat(resource).isNotNull();
        assertThat(resource.getPath()).isEqualTo("/version.properties");
        assertThat(resource.getName()).isEqualTo("version.properties");
        assertThat(read(resource)).contains("undertow.version=");
    }

    private static ClassLoader classLoader() {
        return ClassPathResourceManagerTest.class.getClassLoader();
    }

    private static String read(Resource resource) throws Exception {
        try (InputStream inputStream = resource.getUrl().openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
