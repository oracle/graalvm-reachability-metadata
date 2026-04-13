/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jline.internal;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.assertSame;

public final class TerminalLineSettingsTest {

    @Test
    void inheritInputSetsProcessBuilderToUseInheritedInput() throws Throwable {
        // The redirect branch depends on System.console() being available during class
        // initialization, which is not true for Gradle test workers.
        final MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
            TerminalLineSettings.class,
            MethodHandles.lookup()
        );
        final MethodHandle inheritInput = lookup.findStatic(
            TerminalLineSettings.class,
            "inheritInput",
            MethodType.methodType(ProcessBuilder.class, ProcessBuilder.class)
        );
        final ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", "exit 0");

        final ProcessBuilder configuredProcessBuilder = (ProcessBuilder) inheritInput.invoke(processBuilder);

        assertSame(processBuilder, configuredProcessBuilder);
        assertSame(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectInput());
    }
}
