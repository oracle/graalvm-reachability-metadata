/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.common.utils.LoggingSignalHandler;
import org.junit.jupiter.api.Test;
import sun.misc.Signal;
import sun.misc.SignalHandler;

public class LoggingSignalHandlerAnonymous1Test {

    private static final List<String> REGISTERED_SIGNALS = List.of("TERM", "INT", "HUP");

    @Test
    void registerDelegatesRaisedSignalToPreviouslyInstalledHandler() throws Exception {
        Map<String, SignalHandler> originalHandlers = new LinkedHashMap<>();
        CountDownLatch handledSignal = new CountDownLatch(1);
        AtomicReference<String> handledSignalName = new AtomicReference<>();

        try {
            for (String signalName : REGISTERED_SIGNALS) {
                SignalHandler replacementHandler = signalName.equals("HUP")
                    ? signal -> {
                        handledSignalName.set(signal.getName());
                        handledSignal.countDown();
                    }
                    : signal -> {
                    };
                originalHandlers.put(signalName, Signal.handle(new Signal(signalName), replacementHandler));
            }

            new LoggingSignalHandler().register();
            Signal.raise(new Signal("HUP"));

            assertThat(handledSignal.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(handledSignalName.get()).isEqualTo("HUP");
        } finally {
            restoreOriginalHandlers(originalHandlers);
        }
    }

    private static void restoreOriginalHandlers(Map<String, SignalHandler> originalHandlers) {
        originalHandlers.forEach((signalName, signalHandler) -> Signal.handle(new Signal(signalName), signalHandler));
    }
}
