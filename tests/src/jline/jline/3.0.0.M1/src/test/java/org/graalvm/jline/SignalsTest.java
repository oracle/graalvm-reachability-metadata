/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.utils.Signals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SignalsTest {

    private static final String LOW_IMPACT_SIGNAL = "WINCH";

    @AfterEach
    void restoreDefaultHandler() {
        Signals.registerDefault(LOW_IMPACT_SIGNAL);
    }

    @Test
    void registerInstallsRunnableSignalHandler() {
        Object previousHandler = Signals.register(LOW_IMPACT_SIGNAL, () -> { });

        assertThat(previousHandler).isNull();
    }

    @Test
    void registerDefaultInstallsJvmDefaultSignalHandler() {
        Object previousHandler = Signals.registerDefault(LOW_IMPACT_SIGNAL);

        assertThat(previousHandler).isNull();
    }

    @Test
    void registerIgnoreInstallsJvmIgnoreSignalHandler() {
        Object previousHandler = Signals.registerIgnore(LOW_IMPACT_SIGNAL);

        assertThat(previousHandler).isNull();
    }
}
