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
    void startUsesJansiTwoOutStreamWhenEnabledForSystemOut() {
        ConsoleAppender<String> appender = newJansiConsoleAppender("System.out");

        appender.start();
        try {
            assertThat(appender.isStarted()).isTrue();
            assertThat(appender.getOutputStream()).isSameAs(AnsiConsole.outStream());

            appender.doAppend("jansi two output");

            assertThat(AnsiConsole.systemInstallCalls()).isEqualTo(1);
            assertThat(AnsiConsole.outCalls()).isEqualTo(1);
            assertThat(AnsiConsole.wrapSystemErrCalls()).isZero();
            assertThat(AnsiConsole.outContents()).contains("jansi two output");
        } finally {
            appender.stop();
        }
    }

    @Test
    void startFallsBackToJansiOneWrapperWhenErrMethodIsUnavailable() {
        ConsoleAppender<String> appender = newJansiConsoleAppender("System.err");

        appender.start();
        try {
            assertThat(appender.isStarted()).isTrue();
            assertThat(appender.getOutputStream()).isSameAs(AnsiConsole.wrappedErrStream());

            appender.doAppend("jansi one error output");

            assertThat(AnsiConsole.systemInstallCalls()).isEqualTo(1);
            assertThat(AnsiConsole.outCalls()).isZero();
            assertThat(AnsiConsole.wrapSystemErrCalls()).isEqualTo(1);
            assertThat(AnsiConsole.wrappedErrContents()).contains("jansi one error output");
        } finally {
            appender.stop();
        }
    }

    private ConsoleAppender<String> newJansiConsoleAppender(String target) {
        ContextBase context = new ContextBase();
        context.setName("console-appender-test");

        EchoEncoder<String> encoder = new EchoEncoder<>();
        encoder.setContext(context);
        encoder.start();

        ConsoleAppender<String> appender = new ConsoleAppender<>();
        appender.setName("console");
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setTarget(target);
        appender.setWithJansi(true);
        return appender;
    }
}
