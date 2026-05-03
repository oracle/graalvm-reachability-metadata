/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.utils.Signals;
import org.junit.jupiter.api.Test;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class SignalsTest {

    private static final String TEST_SIGNAL = "WINCH";

    @Test
    void signalHelpersRegisterIgnoreDefaultAndRunnableHandlers() throws Exception {
        Signal signal = new Signal(TEST_SIGNAL);
        SignalHandler originalHandler = Signal.handle(signal, SignalHandler.SIG_DFL);

        try {
            Signals.registerIgnore(TEST_SIGNAL);
            SignalHandler previousAfterIgnore = Signal.handle(signal, SignalHandler.SIG_DFL);
            assertThat(previousAfterIgnore).isSameAs(SignalHandler.SIG_IGN);

            Signals.registerDefault(TEST_SIGNAL);
            SignalHandler previousAfterDefault = Signal.handle(signal, SignalHandler.SIG_IGN);
            assertThat(previousAfterDefault).isSameAs(SignalHandler.SIG_DFL);

            CountDownLatch handled = new CountDownLatch(1);
            Signals.register(TEST_SIGNAL, handled::countDown);
            Signal.raise(signal);

            assertThat(handled.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            Signal.handle(signal, originalHandler);
        }
    }
}
