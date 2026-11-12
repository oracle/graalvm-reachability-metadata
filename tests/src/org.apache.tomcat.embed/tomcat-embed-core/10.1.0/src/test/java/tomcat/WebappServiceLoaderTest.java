/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.file.Path;
import java.util.List;

import org.apache.catalina.startup.WebappServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class WebappServiceLoaderTest {
    @TempDir
    Path directory;

    @Test
    void discoversAndConstructsApplicationServiceProviders() throws Exception {
        try (EmbeddedServer server = new EmbeddedServer(directory)) {
            server.start();
            List<Greeting> services = new WebappServiceLoader<Greeting>(server.context()).load(Greeting.class);
            assertThat(services).hasSize(1);
            assertThat(services.get(0).greet("reader")).isEqualTo("Hello reader");
        }
    }

    public interface Greeting {
        String greet(String name);
    }

    public static class GreetingProvider implements Greeting {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
