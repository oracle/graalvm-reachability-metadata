/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.logging_mailhandler;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.mail.util.logging.CompactFormatter;
import com.sun.mail.util.logging.DurationFilter;
import com.sun.mail.util.logging.MailHandler;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class LogManagerPropertiesTest {
    private static final Object LOG_MANAGER_LOCK = new Object();

    @Test
    void durationFilterParsesIsoDurationConfiguration() throws Throwable {
        withLogManagerConfiguration("""
                com.sun.mail.util.logging.DurationFilter.records=2
                com.sun.mail.util.logging.DurationFilter.duration=PT0.5S
                """, () -> {
                    DurationFilter filter = new DurationFilter();

                    assertThat(filter.isLoggable(recordAt(1_000L))).isTrue();
                    assertThat(filter.isLoggable(recordAt(1_000L))).isTrue();
                    assertThat(filter.isLoggable(recordAt(1_000L))).isFalse();
                    assertThat(filter.isLoggable(recordAt(1_500L))).isTrue();
                });
    }

    @Test
    void mailHandlerInstantiatesConfiguredLoggingComponents() throws Throwable {
        withLogManagerConfiguration("""
                com.sun.mail.util.logging.MailHandler.level=ALL
                com.sun.mail.util.logging.MailHandler.capacity=3
                com.sun.mail.util.logging.MailHandler.filter=com.sun.mail.util.logging.DurationFilter
                com.sun.mail.util.logging.MailHandler.formatter=com.sun.mail.util.logging.CompactFormatter
                com.sun.mail.util.logging.MailHandler.comparator=com.sun.mail.util.logging.SeverityComparator
                com.sun.mail.util.logging.MailHandler.comparator.reverse=true
                com.sun.mail.util.logging.MailHandler.errorManager=java.util.logging.ErrorManager
                com.sun.mail.util.logging.MailHandler.pushFilter=com.sun.mail.util.logging.DurationFilter
                com.sun.mail.util.logging.MailHandler.attachment.filters=com.sun.mail.util.logging.DurationFilter
                com.sun.mail.util.logging.MailHandler.attachment.formatters=com.sun.mail.util.logging.CompactFormatter
                com.sun.mail.util.logging.MailHandler.attachment.names=report.txt
                """, () -> {
                    MailHandler handler = new MailHandler();
                    try {
                        Filter filter = handler.getFilter();

                        assertThat(filter).isInstanceOf(DurationFilter.class);
                        assertThat(handler.getFormatter()).isInstanceOf(CompactFormatter.class);
                        assertThat(handler.getErrorManager()).isNotNull();
                        assertThat(handler.isLoggable(new LogRecord(Level.SEVERE, "configured"))).isTrue();
                    } finally {
                        handler.close();
                    }
                });
    }

    @Test
    void compactFormatterClassifiesUtilityAndReflectionStackFrames() {
        CompactFormatter formatter = new CompactFormatter("%1$tY %5$s %6$s %14$s%n");
        Throwable failure = new IllegalArgumentException("boom");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("java.util.Collections", "emptyList", "Collections.java", 1),
                new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 1),
                new StackTraceElement("java.lang.String", "substring", "String.java", 1)
        });
        LogRecord record = new LogRecord(Level.WARNING, "covered-message");
        record.setThrown(failure);

        String formatted = formatter.format(record);

        assertThat(formatted).contains("covered-message", "IllegalArgumentException", "String.substring");
    }

    @Test
    void mailHandlerReportsMissingConfiguredClassesWithContextClassLoader() throws Throwable {
        withLogManagerConfiguration(missingClassConfiguration(), () -> {
            MailHandler handler = new MailHandler();
            try {
                assertThat(handler.getFormatter()).isNotNull();
            } finally {
                handler.close();
            }
        });
    }

    @Test
    void mailHandlerReportsMissingConfiguredClassesWithoutContextClassLoader() throws Throwable {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            withLogManagerConfiguration(missingClassConfiguration(), () -> {
                MailHandler handler = new MailHandler();
                try {
                    assertThat(handler.getFormatter()).isNotNull();
                } finally {
                    handler.close();
                }
            });
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void mailHandlerLocalVerificationReadsTransportLocalHost() throws Throwable {
        withLogManagerConfiguration("""
                com.sun.mail.util.logging.MailHandler.level=OFF
                com.sun.mail.util.logging.MailHandler.capacity=1
                """, () -> {
                    Properties properties = new Properties();
                    properties.setProperty("verify", "local");
                    properties.setProperty("mail.transport.protocol", "smtp");
                    properties.setProperty("mail.smtp.host", "localhost");
                    properties.setProperty("mail.smtp.localhost", "localhost");
                    properties.setProperty("mail.from", "sender@example.test");
                    properties.setProperty("mail.to", "recipient@example.test");

                    MailHandler handler = new MailHandler(properties);
                    try {
                        assertThat(handler.getMailProperties()).containsEntry("mail.smtp.localhost", "localhost");
                    } finally {
                        handler.close();
                    }
                });
    }

    private static String missingClassConfiguration() {
        return """
                com.sun.mail.util.logging.MailHandler.level=ALL
                com.sun.mail.util.logging.MailHandler.capacity=1
                com.sun.mail.util.logging.MailHandler.filter=example.missing.Filter
                com.sun.mail.util.logging.MailHandler.formatter=example.missing.Formatter
                com.sun.mail.util.logging.MailHandler.comparator=example.missing.Comparator
                com.sun.mail.util.logging.MailHandler.pushFilter=example.missing.PushFilter
                com.sun.mail.util.logging.MailHandler.attachment.filters=example.missing.AttachmentFilter
                com.sun.mail.util.logging.MailHandler.attachment.formatters=example.missing.AttachmentFormatter
                com.sun.mail.util.logging.MailHandler.attachment.names=example.missing.AttachmentName
                """;
    }

    private static LogRecord recordAt(long millis) {
        LogRecord record = new LogRecord(Level.INFO, "duration");
        record.setMillis(millis);
        return record;
    }

    private static void withLogManagerConfiguration(String configuration, Executable executable) throws Throwable {
        synchronized (LOG_MANAGER_LOCK) {
            LogManager logManager = LogManager.getLogManager();
            try {
                byte[] bytes = configuration.getBytes(StandardCharsets.UTF_8);
                logManager.readConfiguration(new ByteArrayInputStream(bytes));
                executable.execute();
            } finally {
                logManager.reset();
            }
        }
    }
}
