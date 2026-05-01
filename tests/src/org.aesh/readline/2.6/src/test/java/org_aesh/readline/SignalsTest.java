/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import java.util.concurrent.atomic.AtomicInteger;

import org.aesh.readline.terminal.utils.Signals;
import org.junit.jupiter.api.Test;

import sun.misc.Signal;
import sun.misc.SignalHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class SignalsTest {
    private static final String TEST_SIGNAL_NAME = "WINCH";

    @Test
    void registeredHandlerCanBeCapturedAndInvoked() {
        Signal signal = new Signal(TEST_SIGNAL_NAME);
        SignalHandler originalHandler = Signal.handle(signal, SignalHandler.SIG_DFL);
        Signal.handle(signal, originalHandler);

        AtomicInteger handlerInvocations = new AtomicInteger();
        try {
            Signals.register(TEST_SIGNAL_NAME, handlerInvocations::incrementAndGet, SignalsTest.class.getClassLoader());
            SignalHandler registeredHandler = Signal.handle(signal, originalHandler);

            Signals.invokeHandler(TEST_SIGNAL_NAME, registeredHandler);

            assertThat(handlerInvocations).hasValue(1);
        } finally {
            Signal.handle(signal, originalHandler);
        }
    }

    @Test
    void defaultAndIgnoreHandlersCanBeRegistered() {
        Signal signal = new Signal(TEST_SIGNAL_NAME);
        SignalHandler originalHandler = Signal.handle(signal, SignalHandler.SIG_DFL);
        Signal.handle(signal, originalHandler);

        try {
            Signals.registerDefault(TEST_SIGNAL_NAME);
            SignalHandler defaultHandler = Signal.handle(signal, originalHandler);
            assertThat(defaultHandler).isSameAs(SignalHandler.SIG_DFL);

            Signals.registerIgnore(TEST_SIGNAL_NAME);
            SignalHandler ignoreHandler = Signal.handle(signal, originalHandler);
            assertThat(ignoreHandler).isSameAs(SignalHandler.SIG_IGN);
        } finally {
            Signal.handle(signal, originalHandler);
        }
    }
}
