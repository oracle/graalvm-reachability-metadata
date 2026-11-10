/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.FileAppender;
import ch_qos_logback.logback_core.ConsoleAppenderTest.AttachableContext;
import ch_qos_logback.logback_core.ConsoleAppenderTest.CoreAppenderConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class FileAppenderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void xmlAppendOptionTruncatesExistingFileAndKeepsNewEvents() throws Exception {
        Path logFile = temporaryDirectory.resolve("application.log");
        Files.writeString(logFile, "previous run");
        AttachableContext context = new AttachableContext();
        context.putProperty("logFile", logFile.toString());
        CoreAppenderConfigurator configurator = new CoreAppenderConfigurator();
        configurator.setContext(context);

        try {
            configurator.doConfigure(new ByteArrayInputStream("""
                    <configuration>
                        <appender name="FILE" class="ch.qos.logback.core.FileAppender">
                            <file>${logFile}</file>
                            <append>false</append>
                            <encoder class="ch.qos.logback.core.encoder.EchoEncoder"/>
                        </appender>
                        <appender-ref ref="FILE"/>
                    </configuration>
                    """.getBytes(StandardCharsets.UTF_8)));

            FileAppender<?> appender = (FileAppender<?>) context.getAppender("FILE");
            assertThat(appender.isStarted()).isTrue();
            assertThat(appender.isAppend()).isFalse();
            context.getAppender("FILE").doAppend("first event");
            context.getAppender("FILE").doAppend("second event");
            context.detachAndStopAllAppenders();

            assertThat(Files.readString(logFile)).isEqualTo(
                    "first event" + System.lineSeparator() + "second event" + System.lineSeparator());
        } finally {
            context.detachAndStopAllAppenders();
            context.stop();
        }
    }
}
