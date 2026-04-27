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

import static org.assertj.core.api.Assertions.assertThat;

public class SignalsTest {

    @Test
    void registersCustomIgnoreAndDefaultHandlersForWindowResizeSignals() {
        String signalName = "WINCH";

        try {
            Signals.register(signalName, () -> {
            });
            SignalHandler registeredHandler = Signal.handle(new Signal(signalName), SignalHandler.SIG_DFL);
            assertThat(registeredHandler)
                    .isNotNull()
                    .isNotSameAs(SignalHandler.SIG_DFL)
                    .isNotSameAs(SignalHandler.SIG_IGN);

            Signals.registerIgnore(signalName);
            SignalHandler ignoredHandler = Signal.handle(new Signal(signalName), registeredHandler);
            assertThat(ignoredHandler).isSameAs(SignalHandler.SIG_IGN);

            Signals.registerDefault(signalName);
            SignalHandler defaultHandler = Signal.handle(new Signal(signalName), SignalHandler.SIG_IGN);
            assertThat(defaultHandler).isSameAs(SignalHandler.SIG_DFL);
        } finally {
            Signal.handle(new Signal(signalName), SignalHandler.SIG_DFL);
        }
    }
}
