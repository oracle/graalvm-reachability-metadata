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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class OrgApacheKafkaCommonUtilsLoggingSignalHandlerAnonymous1Test {

    private static final List<String> LOGGED_SIGNALS = List.of("TERM", "INT", "HUP");

    @Test
    void registeredHandlersDelegateToPreviousJvmSignalHandlers() throws Exception {
        Map<String, Signal> signals = new LinkedHashMap<>();
        Map<String, SignalHandler> originalHandlers = new LinkedHashMap<>();
        Map<String, AtomicInteger> previousHandlerInvocations = new LinkedHashMap<>();

        try {
            for (String signalName : LOGGED_SIGNALS) {
                Signal signal = new Signal(signalName);
                AtomicInteger invocations = new AtomicInteger();
                SignalHandler previousHandler = handledSignal -> invocations.incrementAndGet();
                signals.put(signalName, signal);
                previousHandlerInvocations.put(signalName, invocations);
                originalHandlers.put(signalName, Signal.handle(signal, previousHandler));
            }

            new LoggingSignalHandler().register();

            Map<String, SignalHandler> loggingHandlers = new LinkedHashMap<>();
            for (Map.Entry<String, Signal> entry : signals.entrySet()) {
                SignalHandler loggingHandler = Signal.handle(
                        entry.getValue(),
                        originalHandlers.get(entry.getKey()));
                loggingHandlers.put(entry.getKey(), loggingHandler);
            }

            for (Map.Entry<String, SignalHandler> entry : loggingHandlers.entrySet()) {
                assertThatCode(() -> entry.getValue().handle(signals.get(entry.getKey())))
                        .doesNotThrowAnyException();
                assertThat(previousHandlerInvocations.get(entry.getKey()).get()).isEqualTo(1);
            }
        } finally {
            for (Map.Entry<String, SignalHandler> entry : originalHandlers.entrySet()) {
                Signal.handle(signals.get(entry.getKey()), entry.getValue());
            }
        }
    }
}
