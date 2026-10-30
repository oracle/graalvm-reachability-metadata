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
import org.springframework.boot.ansi.AnsiOutput.Enabled;

import static org.assertj.core.api.Assertions.assertThat;

public class AnsiOutputTest {

    @Test
    void detectsConsoleSupportBeforeRenderingStatusText() {
        Enabled previous = AnsiOutput.getEnabled();
        try {
            AnsiOutput.setConsoleAvailable(null);
            AnsiOutput.setEnabled(Enabled.DETECT);
            String status = AnsiOutput.toString(AnsiColor.GREEN, "ready");

            assertThat(status).isIn("ready", "\u001b[32mready\u001b[0;39m");
            assertThat(AnsiOutput.encode(AnsiColor.GREEN)).isEqualTo(status.equals("ready") ? "" : "\u001b[32m");
        }
        finally {
            AnsiOutput.setEnabled(previous);
            AnsiOutput.setConsoleAvailable(null);
        }
    }
}
