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

    @Test
    void delegatesRaisedSignalToPreviousSignalHandler() throws Exception {
        CountDownLatch previousHandlerInvoked = new CountDownLatch(1);
        Map<Signal, SignalHandler> originalHandlers = new LinkedHashMap<>();
        Signal hup = new Signal("HUP");

        try {
            for (String signalName : new String[] {"TERM", "INT", "HUP"}) {
                Signal signal = new Signal(signalName);
                SignalHandler previousHandler = Signal.handle(signal, handledSignal -> {
                    if ("HUP".equals(handledSignal.getName())) {
                        previousHandlerInvoked.countDown();
                    }
                });
                originalHandlers.put(signal, previousHandler);
            }

            new LoggingSignalHandler().register();
            restoreOriginalHandlersExceptHup(originalHandlers);
            Signal.raise(hup);

            assertThat(previousHandlerInvoked.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            restoreOriginalHandlers(originalHandlers);
        }
    }

    private static void restoreOriginalHandlersExceptHup(Map<Signal, SignalHandler> originalHandlers) {
        for (Map.Entry<Signal, SignalHandler> originalHandler : originalHandlers.entrySet()) {
            if (!"HUP".equals(originalHandler.getKey().getName())) {
                Signal.handle(originalHandler.getKey(), originalHandler.getValue());
            }
        }
    }

    private static void restoreOriginalHandlers(Map<Signal, SignalHandler> originalHandlers) {
        for (Map.Entry<Signal, SignalHandler> originalHandler : originalHandlers.entrySet()) {
            Signal.handle(originalHandler.getKey(), originalHandler.getValue());
        }
    }
}
