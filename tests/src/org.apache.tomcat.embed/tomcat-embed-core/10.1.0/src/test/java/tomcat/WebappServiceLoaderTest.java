/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.util.List;

import org.apache.catalina.startup.WebappServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebappServiceLoaderTest {
    @TempDir
    File base;

    @Test
    void discoversAndConstructsServiceProviders() throws Exception {
        try (EmbeddedApplication application = new EmbeddedApplication(base)) {
            application.start();
            WebappServiceLoader<Greeting> loader = new WebappServiceLoader<>(application.context);
            List<Greeting> providers = loader.load(Greeting.class);
            assertEquals(1, providers.size());
            assertEquals("hello", providers.get(0).message());
        }
    }

    public interface Greeting {
        String message();
    }

    public static class GreetingProvider implements Greeting {
        @Override
        public String message() {
            return "hello";
        }
    }
}
