/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.naming.Context;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardServer;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NamingContextListenerTest {

    @Test
    void serverStartupBindsCustomEnvironmentEntriesConstructedByTypeName() throws Exception {
        String previousUseNaming = System.getProperty("catalina.useNaming");
        System.setProperty("catalina.useNaming", "true");
        StandardServer server = new StandardServer();
        server.setPeriodicEventDelay(0);
        server.getGlobalNamingResources().addEnvironment(environment("stringEntry",
                StringConstructedEnvironmentEntry.class.getName(), "configured"));
        server.getGlobalNamingResources().addEnvironment(environment("charEntry",
                CharConstructedEnvironmentEntry.class.getName(), "Q"));

        try {
            server.start();

            Context globalNamingContext = server.getGlobalNamingContext();
            assertThat(globalNamingContext.lookup("stringEntry"))
                    .isInstanceOfSatisfying(StringConstructedEnvironmentEntry.class,
                            entry -> assertThat(entry.value()).isEqualTo("configured"));
            assertThat(globalNamingContext.lookup("charEntry"))
                    .isInstanceOfSatisfying(CharConstructedEnvironmentEntry.class,
                            entry -> assertThat(entry.value()).isEqualTo('Q'));
        } finally {
            stopAndDestroy(server);
            restoreUseNaming(previousUseNaming);
        }
    }

    private static ContextEnvironment environment(String name, String type, String value) {
        ContextEnvironment environment = new ContextEnvironment();
        environment.setName(name);
        environment.setType(type);
        environment.setValue(value);
        return environment;
    }

    private static void stopAndDestroy(StandardServer server) throws LifecycleException {
        try {
            if (server.getState().isAvailable()) {
                server.stop();
            }
        } finally {
            if (server.getState() != LifecycleState.DESTROYED) {
                server.destroy();
            }
        }
    }

    private static void restoreUseNaming(String previousUseNaming) {
        if (previousUseNaming == null) {
            System.clearProperty("catalina.useNaming");
        } else {
            System.setProperty("catalina.useNaming", previousUseNaming);
        }
    }

    public static final class StringConstructedEnvironmentEntry {

        private final String value;

        public StringConstructedEnvironmentEntry(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    public static final class CharConstructedEnvironmentEntry {

        private final char value;

        public CharConstructedEnvironmentEntry(char value) {
            this.value = value;
        }

        char value() {
            return value;
        }
    }
}
