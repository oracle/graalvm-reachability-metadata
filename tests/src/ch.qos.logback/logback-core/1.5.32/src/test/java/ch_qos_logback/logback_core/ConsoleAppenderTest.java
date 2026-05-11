/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.encoder.EchoEncoder;
import org.fusesource.jansi.AnsiConsole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleAppenderTest {
    @BeforeEach
    void resetAnsiConsole() {
        AnsiConsole.reset();
    }

    @Test
    void withJansiUsesAnsiConsoleOutMethodForSystemOut() {
        ConsoleAppender<String> appender = newConsoleAppender("System.out");

        appender.start();
        appender.doAppend("jansi out branch");

        assertThat(appender.isStarted()).isTrue();
        assertThat(AnsiConsole.systemInstallCount()).isEqualTo(1);
        assertThat(AnsiConsole.outCount()).isEqualTo(1);
        assertThat(AnsiConsole.wrapSystemErrCount()).isZero();
        assertThat(AnsiConsole.outContent()).contains("jansi out branch");
    }

    @Test
    void withJansiFallsBackToWrapSystemErrWhenErrMethodIsUnavailable() {
        ConsoleAppender<String> appender = newConsoleAppender("System.err");

        appender.start();
        appender.doAppend("jansi err wrapper branch");

        assertThat(appender.isStarted()).isTrue();
        assertThat(AnsiConsole.systemInstallCount()).isEqualTo(1);
        assertThat(AnsiConsole.outCount()).isZero();
        assertThat(AnsiConsole.wrapSystemErrCount()).isEqualTo(1);
        assertThat(AnsiConsole.errContent()).contains("jansi err wrapper branch");
    }

    private static ConsoleAppender<String> newConsoleAppender(String target) {
        ContextBase context = new ContextBase();
        context.setName("console-appender-test-context");

        EchoEncoder<String> encoder = new EchoEncoder<>();
        encoder.setContext(context);
        encoder.start();

        ConsoleAppender<String> appender = new ConsoleAppender<>();
        appender.setContext(context);
        appender.setName("console-appender-test");
        appender.setEncoder(encoder);
        appender.setTarget(target);
        appender.setWithJansi(true);
        return appender;
    }
}
