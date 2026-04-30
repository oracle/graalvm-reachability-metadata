/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.common.utils.LoggingSignalHandler;
import org.junit.jupiter.api.Test;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class LoggingSignalHandlerAnonymous1Test {
    private static final String RAISED_SIGNAL = "TERM";
    private static final String[] REGISTERED_SIGNALS = {"TERM", "INT", "HUP"};

    @Test
    void handlesRaisedSignalAndDelegatesToPreviousHandler() throws Exception {
        CountDownLatch previousHandlerInvoked = new CountDownLatch(1);
        Map<Signal, SignalHandler> originalHandlers = installPreviousHandlers(previousHandlerInvoked);

        try {
            assertDoesNotThrow(() -> new LoggingSignalHandler().register());

            Signal.raise(new Signal(RAISED_SIGNAL));

            assertTrue(previousHandlerInvoked.await(10, TimeUnit.SECONDS));
        } finally {
            restoreHandlers(originalHandlers);
        }
    }

    private static Map<Signal, SignalHandler> installPreviousHandlers(CountDownLatch previousHandlerInvoked) {
        Map<Signal, SignalHandler> originalHandlers = new LinkedHashMap<>();
        SignalHandler previousHandler = signal -> {
            if (RAISED_SIGNAL.equals(signal.getName())) {
                previousHandlerInvoked.countDown();
            }
        };

        for (String signalName : REGISTERED_SIGNALS) {
            Signal signal = new Signal(signalName);
            SignalHandler originalHandler = Signal.handle(signal, previousHandler);
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
