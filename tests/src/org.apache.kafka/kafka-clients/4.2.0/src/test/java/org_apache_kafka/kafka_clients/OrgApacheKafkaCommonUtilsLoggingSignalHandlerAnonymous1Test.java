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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonUtilsLoggingSignalHandlerAnonymous1Test {

    private static final String[] SIGNAL_NAMES = {"TERM", "INT", "HUP"};

    @Test
    void raisedSignalIsResolvedByNameAndDelegatedToPreviousHandler() throws Exception {
        CountDownLatch delegatedSignal = new CountDownLatch(1);
        AtomicReference<String> observedSignalName = new AtomicReference<>();
        Map<String, SignalHandler> originalHandlers = installSafeHandlers(delegatedSignal, observedSignalName);

        try {
            new LoggingSignalHandler().register();

            Signal.raise(new Signal("HUP"));

            assertThat(delegatedSignal.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(observedSignalName).hasValue("HUP");
        } finally {
            restoreHandlers(originalHandlers);
        }
    }

    private static Map<String, SignalHandler> installSafeHandlers(CountDownLatch delegatedSignal, AtomicReference<String> observedSignalName) {
        Map<String, SignalHandler> originalHandlers = new LinkedHashMap<>();
        for (String signalName : SIGNAL_NAMES) {
            Signal signal = new Signal(signalName);
            SignalHandler previousHandler = Signal.handle(signal, receivedSignal -> {
                if ("HUP".equals(receivedSignal.getName())) {
                    observedSignalName.set(receivedSignal.getName());
                    delegatedSignal.countDown();
                }
            });
            originalHandlers.put(signalName, previousHandler);
        }
        return originalHandlers;
    }

    private static void restoreHandlers(Map<String, SignalHandler> originalHandlers) {
        for (Map.Entry<String, SignalHandler> entry : originalHandlers.entrySet()) {
            Signal.handle(new Signal(entry.getKey()), entry.getValue());
        }
    }
}
