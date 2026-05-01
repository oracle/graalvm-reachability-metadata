/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_daemon.commons_daemon;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.support.DaemonLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DaemonLoaderTest {
    @BeforeEach
    void resetFixtureState() {
        CheckOnlyDaemon.instances = 0;
        MethodStyleDaemon.reset();
        InterfaceStyleDaemon.reset();
    }

    @Test
    void checkLoadsAndInstantiatesNamedClass() {
        boolean loadable = DaemonLoader.check(CheckOnlyDaemon.class.getName());

        assertThat(loadable).isTrue();
        assertThat(CheckOnlyDaemon.instances).isEqualTo(1);
    }

    @Test
    void loadInvokesLifecycleMethodsOnMethodStyleDaemon() {
        String[] arguments = {"alpha", "beta"};

        assertThat(DaemonLoader.load(MethodStyleDaemon.class.getName(), arguments)).isTrue();
        assertThat(MethodStyleDaemon.events).containsExactly("constructed", "init");
        assertThat(MethodStyleDaemon.initArguments).containsExactly(arguments);

        assertThat(DaemonLoader.start()).isTrue();
        assertThat(DaemonLoader.signal()).isTrue();
        assertThat(DaemonLoader.stop()).isTrue();
        assertThat(DaemonLoader.destroy()).isTrue();
        assertThat(MethodStyleDaemon.events).containsExactly(
                "constructed", "init", "start", "signal", "stop", "destroy");
    }

    @Test
    void loadInvokesLifecycleMethodsOnDaemonInterfaceImplementation() {
        String[] arguments = {"from", "context"};

        assertThat(DaemonLoader.load(InterfaceStyleDaemon.class.getName(), arguments)).isTrue();
        assertThat(InterfaceStyleDaemon.events).containsExactly("constructed", "init");
        assertThat(InterfaceStyleDaemon.initArguments).containsExactly(arguments);
        assertThat(InterfaceStyleDaemon.receivedController).isNotNull();

        assertThat(DaemonLoader.start()).isTrue();
        assertThat(DaemonLoader.signal()).isTrue();
        assertThat(DaemonLoader.stop()).isTrue();
        assertThat(DaemonLoader.destroy()).isTrue();
        assertThat(InterfaceStyleDaemon.events).containsExactly(
                "constructed", "init", "start", "signal", "stop", "destroy");
    }

    public static final class CheckOnlyDaemon {
        private static int instances;

        public CheckOnlyDaemon() {
            instances++;
        }
    }

    public static final class MethodStyleDaemon {
        private static final List<String> events = new ArrayList<>();
        private static List<String> initArguments = new ArrayList<>();

        public MethodStyleDaemon() {
            events.add("constructed");
        }

        public static void reset() {
            events.clear();
            initArguments = new ArrayList<>();
        }

        public void init(String[] arguments) {
            initArguments = Arrays.asList(arguments.clone());
            events.add("init");
        }

        public void start() {
            events.add("start");
        }

        public void signal() {
            events.add("signal");
        }

        public void stop() {
            events.add("stop");
        }

        public void destroy() {
            events.add("destroy");
        }
    }

    public static final class InterfaceStyleDaemon implements Daemon {
        private static final List<String> events = new ArrayList<>();
        private static List<String> initArguments = new ArrayList<>();
        private static Object receivedController;

        public InterfaceStyleDaemon() {
            events.add("constructed");
        }

        public static void reset() {
            events.clear();
            initArguments = new ArrayList<>();
            receivedController = null;
        }

        @Override
        public void init(DaemonContext context) {
            initArguments = Arrays.asList(context.getArguments().clone());
            receivedController = context.getController();
            events.add("init");
        }

        @Override
        public void start() {
            events.add("start");
        }

        public void signal() {
            events.add("signal");
        }

        @Override
        public void stop() {
            events.add("stop");
        }

        @Override
        public void destroy() {
            events.add("destroy");
        }
    }
}
