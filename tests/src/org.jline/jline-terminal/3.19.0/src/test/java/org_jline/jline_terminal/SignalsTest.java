/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jline.jline_terminal;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jline.utils.Signals;
import org.junit.jupiter.api.Test;

public class SignalsTest {
    private static final Logger JLINE_LOGGER = Logger.getLogger("org.jline");
    private static final String WINDOW_CHANGE_SIGNAL = "WINCH";

    @Test
    public void registerDefaultInstallsAndRestoresNativeDefaultHandler() {
        Level previousLevel = enableTraceLogging();
        try {
            assertThatCode(() -> {
                Object previousHandler = Signals.registerDefault(WINDOW_CHANGE_SIGNAL);
                Signals.unregister(WINDOW_CHANGE_SIGNAL, previousHandler);
            }).doesNotThrowAnyException();
        } finally {
            restoreLogging(previousLevel);
        }
    }

    @Test
    public void registerInstallsRunnableProxyAndRestoresPreviousHandler() {
        Level previousLevel = enableTraceLogging();
        AtomicInteger handledSignals = new AtomicInteger();
        try {
            assertThatCode(() -> {
                Object previousHandler = Signals.register(
                        WINDOW_CHANGE_SIGNAL,
                        handledSignals::incrementAndGet,
                        SignalsTest.class.getClassLoader());
                Signals.unregister(WINDOW_CHANGE_SIGNAL, previousHandler);
            }).doesNotThrowAnyException();
        } finally {
            restoreLogging(previousLevel);
        }
    }

    private static Level enableTraceLogging() {
        Level previousLevel = JLINE_LOGGER.getLevel();
        JLINE_LOGGER.setLevel(Level.FINEST);
        return previousLevel;
    }

    private static void restoreLogging(Level previousLevel) {
        JLINE_LOGGER.setLevel(previousLevel);
    }
}
