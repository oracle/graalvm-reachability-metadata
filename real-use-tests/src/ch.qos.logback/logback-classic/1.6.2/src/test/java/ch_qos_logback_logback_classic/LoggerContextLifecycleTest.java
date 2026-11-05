/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback_logback_classic;

import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Observes application logging through Logback's logger-context lifecycle subsystem.
 *
 * A service registers a lifecycle listener so its logging integration can react when the shared
 * context starts, is reset during reconfiguration, changes a logger level, and shuts down. The
 * service uses those callbacks to keep its integration state aligned with Logback's state.
 */
class LoggerContextLifecycleTest {

    private LoggerContext context;

    private LifecycleObserver observer;

    @BeforeEach
    void startApplicationLoggingContext() {
        context = new LoggerContext();
        observer = new LifecycleObserver();
        context.addListener(observer);
        context.start();
    }

    @AfterEach
    void stopApplicationLoggingContext() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void reportsContextAndLoggerLifecycleToTheApplication() {
        Logger serviceLogger = context.getLogger(OrderService.class);
        serviceLogger.setLevel(Level.WARN);
        context.reset();
        context.stop();

        assertThat(observer.startedContext).isSameAs(context);
        assertThat(observer.levelChanges).containsExactly("WARN");
        assertThat(observer.lifecycleEvents).containsExactly("start", "reset", "reset", "stop");
        assertThat(observer.resetContext).isSameAs(context);
        assertThat(observer.stoppedContext).isSameAs(context);
    }

    /** A service-owned integration that follows the lifecycle of Logback's context. */
    static final class LifecycleObserver implements LoggerContextListener {

        private final List<String> lifecycleEvents = new ArrayList<>();

        private final List<String> levelChanges = new ArrayList<>();

        private LoggerContext startedContext;

        private LoggerContext resetContext;

        private LoggerContext stoppedContext;

        @Override
        public boolean isResetResistant() {
            return true;
        }

        @Override
        public void onStart(LoggerContext context) {
            startedContext = context;
            lifecycleEvents.add("start");
        }

        @Override
        public void onReset(LoggerContext context) {
            resetContext = context;
            lifecycleEvents.add("reset");
        }

        @Override
        public void onStop(LoggerContext context) {
            stoppedContext = context;
            lifecycleEvents.add("stop");
        }

        @Override
        public void onLevelChange(Logger logger, Level level) {
            levelChanges.add(level.toString());
        }
    }

    /** Represents an application component that owns a logger. */
    static final class OrderService {
    }
}
