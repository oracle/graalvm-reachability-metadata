/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon.helidon;

import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.Main;
import io.helidon.common.Weight;
import io.helidon.spi.HelidonShutdownHandler;
import io.helidon.spi.HelidonStartupProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HelidonTest {
    private static final AtomicInteger START_COUNT = new AtomicInteger();
    private static final AtomicReference<List<String>> START_ARGUMENTS = new AtomicReference<>();

    @Test
    void serviceLoaderDiscoversStartupProvider() {
        List<HelidonStartupProvider> providers = ServiceLoader.load(HelidonStartupProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertThat(providers.stream()
                .anyMatch(provider -> provider instanceof RecordingStartupProvider))
                .isTrue();
    }

    @Test
    void mainDelegatesToDiscoveredStartupProvider() {
        START_COUNT.set(0);
        START_ARGUMENTS.set(List.of());

        Main.main(new String[] {"--server.port=0", "app.message=hello"});

        assertThat(START_COUNT).hasValue(1);
        assertThat(START_ARGUMENTS.get())
                .containsExactly("--server.port=0", "app.message=hello");
    }

    @Test
    void shutdownHandlersCanBeRegisteredAndRemovedWithoutChangingExistingLogHandlers() {
        Logger rootLogger = Logger.getLogger("");
        Handler markerHandler = new RecordingHandler();
        AtomicInteger shutdownCalls = new AtomicInteger();
        HelidonShutdownHandler shutdownHandler = shutdownCalls::incrementAndGet;

        rootLogger.addHandler(markerHandler);
        try {
            assertThat(Arrays.asList(rootLogger.getHandlers())).contains(markerHandler);

            Main.addShutdownHandler(shutdownHandler);
            assertThat(Arrays.asList(rootLogger.getHandlers())).contains(markerHandler);

            Main.removeShutdownHandler(shutdownHandler);
            assertThat(shutdownCalls).hasValue(0);
        } finally {
            Main.removeShutdownHandler(shutdownHandler);
            rootLogger.removeHandler(markerHandler);
            markerHandler.close();
        }
    }

    @Weight(1_000.0)
    public static final class RecordingStartupProvider implements HelidonStartupProvider {
        public RecordingStartupProvider() {
        }

        @Override
        public void start(String[] args) {
            START_COUNT.incrementAndGet();
            START_ARGUMENTS.set(List.copyOf(Arrays.asList(args)));
        }
    }

    private static final class RecordingHandler extends Handler {
        @Override
        public void publish(LogRecord record) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
