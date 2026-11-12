/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.startup.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BootstrapTest {
    @Test
    void startsAndStopsConfiguredDaemonWithoutAwaitingShutdownSocket() throws Exception {
        Events.types.clear();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Bootstrap bootstrap = new Bootstrap();
        boolean initialized = false;
        try {
            bootstrap.init(new String[] {"-config", "conf/server.xml"});
            initialized = true;
            bootstrap.setAwait(false);
            bootstrap.start();
            assertThat(Events.types).contains("after_start");
            bootstrap.stopServer();
            assertThat(Events.types).contains("after_stop");
        } finally {
            try {
                if (initialized) {
                    bootstrap.stop();
                }
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        }
    }

    public static class Events implements LifecycleListener {
        private static final List<String> types = new ArrayList<>();

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            types.add(event.getType());
        }
    }
}
