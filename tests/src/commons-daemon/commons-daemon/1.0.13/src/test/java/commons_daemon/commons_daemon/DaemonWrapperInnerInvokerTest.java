/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_daemon.commons_daemon;

import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonController;
import org.apache.commons.daemon.support.DaemonWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DaemonWrapperInnerInvokerTest {
    @BeforeEach
    void resetFixtureState() {
        RecordingLifecycle.reset();
    }

    @Test
    void initValidatesAndLifecycleMethodsInvokeConfiguredApplicationClass() throws Exception {
        DaemonWrapper wrapper = new DaemonWrapper();
        String[] arguments = {
                "-start", RecordingLifecycle.class.getName(),
                "-start-method", "startApplication",
                "-stop", RecordingLifecycle.class.getName(),
                "-stop-method", "stopApplication",
                "-stop-argument", "shutdown-now",
                "alpha", "beta"
        };

        wrapper.init(new StaticDaemonContext(arguments));
        wrapper.start();
        wrapper.stop();

        assertThat(RecordingLifecycle.events).containsExactly(
                "constructed", "start:[alpha, beta]",
                "constructed", "stop:[shutdown-now]"
        );
    }

    private static final class StaticDaemonContext implements DaemonContext {
        private final String[] arguments;

        private StaticDaemonContext(String[] arguments) {
            this.arguments = arguments.clone();
        }

        @Override
        public DaemonController getController() {
            return null;
        }

        @Override
        public String[] getArguments() {
            return arguments.clone();
        }
    }

    public static final class RecordingLifecycle {
        private static final List<String> events = new ArrayList<>();

        public RecordingLifecycle() {
            events.add("constructed");
        }

        public static void reset() {
            events.clear();
        }

        public void startApplication(String[] arguments) {
            events.add("start:" + Arrays.toString(arguments));
        }

        public void stopApplication(String[] arguments) {
            events.add("stop:" + Arrays.toString(arguments));
        }
    }
}
