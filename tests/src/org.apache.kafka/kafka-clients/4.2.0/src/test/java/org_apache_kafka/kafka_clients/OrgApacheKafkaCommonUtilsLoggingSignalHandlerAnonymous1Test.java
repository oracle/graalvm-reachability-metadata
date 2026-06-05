/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.utils.LoggingSignalHandler;
import org.junit.jupiter.api.Test;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonUtilsLoggingSignalHandlerAnonymous1Test {
    private static final String RAISED_SIGNAL_NAME = "TERM";
    private static final String[] REGISTERED_SIGNAL_NAMES = {"TERM", "INT", "HUP"};
    private static final Object SIGNAL_LOCK = new Object();

    @Test
    void raisedSignalInvokesPreviouslyRegisteredHandler() throws Exception {
        synchronized (SIGNAL_LOCK) {
            CountDownLatch handled = new CountDownLatch(1);
            SignalHandler observingHandler = signal -> handled.countDown();
            Map<Signal, SignalHandler> originalHandlers = installObservingHandlers(observingHandler);

            try {
                new LoggingSignalHandler().register();

                Signal.raise(new Signal(RAISED_SIGNAL_NAME));

                assertThat(handled.await(2, TimeUnit.SECONDS)).isTrue();
            } finally {
                restoreHandlers(originalHandlers);
            }
        }
    }

    private static Map<Signal, SignalHandler> installObservingHandlers(SignalHandler observingHandler) {
        Map<Signal, SignalHandler> originalHandlers = new LinkedHashMap<>();
        for (String signalName : REGISTERED_SIGNAL_NAMES) {
            Signal signal = new Signal(signalName);
            SignalHandler originalHandler = Signal.handle(signal, observingHandler);
            originalHandlers.put(signal, originalHandler);
        }
        return originalHandlers;
    }

    private static void restoreHandlers(Map<Signal, SignalHandler> originalHandlers) {
        for (Map.Entry<Signal, SignalHandler> entry : originalHandlers.entrySet()) {
            Signal.handle(entry.getKey(), entry.getValue());
        }
    }
}
