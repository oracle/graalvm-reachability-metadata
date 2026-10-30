/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;

import static org.assertj.core.api.Assertions.assertThat;

public class AnsiOutputTest {
    @Test
    void detectsConsoleSupportAndPreservesTextWhenFormatting() {
        AnsiOutput.Enabled previous = AnsiOutput.getEnabled();
        try {
            AnsiOutput.setEnabled(AnsiOutput.Enabled.DETECT);
            AnsiOutput.setConsoleAvailable(null);
            String detected = AnsiOutput.toString(AnsiColor.GREEN, "ready");
            assertThat(detected).isIn("ready", "\u001B[32mready\u001B[0;39m");

            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS);
            assertThat(AnsiOutput.toString(AnsiColor.RED, "stop")).isEqualTo("\u001B[31mstop\u001B[0;39m");
            AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER);
            assertThat(AnsiOutput.toString(AnsiColor.RED, "stop")).isEqualTo("stop");
        } finally {
            AnsiOutput.setEnabled(previous);
            AnsiOutput.setConsoleAvailable(null);
        }
    }
}
