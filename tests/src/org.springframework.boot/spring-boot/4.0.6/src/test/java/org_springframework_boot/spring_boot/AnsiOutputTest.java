/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiOutput.Enabled;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.support.AnsiOutputApplicationListener;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class AnsiOutputTest {
    @Test
    void bindsTheAnsiModeFromThePreparedApplicationEnvironment() {
        Enabled previous = AnsiOutput.getEnabled();
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("terminal", Map.of(
                "spring.output.ansi.enabled", "always")));
        ApplicationEnvironmentPreparedEvent event = new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(), new SpringApplication(AnsiOutputTest.class), new String[0], environment);
        try {
            new AnsiOutputApplicationListener().onApplicationEvent(event);

            assertThat(AnsiOutput.getEnabled()).isEqualTo(Enabled.ALWAYS);
            assertThat(AnsiOutput.toString(AnsiColor.GREEN, "configured"))
                    .isEqualTo("\u001b[32mconfigured\u001b[0;39m");
        } finally {
            AnsiOutput.setEnabled(previous);
            AnsiOutput.setConsoleAvailable(null);
        }
    }

    @Test
    void formatsTerminalAwareAndExplicitlyEnabledOutput() {
        Enabled previous = AnsiOutput.getEnabled();
        try {
            AnsiOutput.setEnabled(Enabled.DETECT);
            assertThat(AnsiOutput.toString(AnsiColor.GREEN, "ready"))
                    .isIn("ready", "\u001b[32mready\u001b[0;39m");

            AnsiOutput.setEnabled(Enabled.ALWAYS);
            assertThat(AnsiOutput.toString(AnsiColor.GREEN, "ready"))
                    .isEqualTo("\u001b[32mready\u001b[0;39m");
            AnsiOutput.setEnabled(Enabled.NEVER);
            assertThat(AnsiOutput.toString(AnsiColor.RED, "stopped")).isEqualTo("stopped");
        } finally {
            AnsiOutput.setEnabled(previous);
        }
    }
}
