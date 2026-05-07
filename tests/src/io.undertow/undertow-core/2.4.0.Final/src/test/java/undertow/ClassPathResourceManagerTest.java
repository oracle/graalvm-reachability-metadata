/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.Version;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.Resource;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathResourceManagerTest {

    @Test
    public void loadsResourceFromPackagePrefix() throws IOException {
        try (ClassPathResourceManager resourceManager = new ClassPathResourceManager(
                Version.class.getClassLoader(), Version.class.getPackage())) {
            Resource resource = resourceManager.getResource("/version.properties");

            assertThat(resource).isNotNull();
            assertThat(resource.getPath()).isEqualTo("/version.properties");
            assertThat(resource.getName()).isEqualTo("version.properties");
            assertThat(resource.getUrl().toExternalForm()).contains("io/undertow/version.properties");
        }
    }
}
