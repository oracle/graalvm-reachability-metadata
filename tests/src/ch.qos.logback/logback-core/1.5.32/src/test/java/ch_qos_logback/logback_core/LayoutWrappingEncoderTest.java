/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch_qos_logback.logback_core.ConsoleAppenderTest.AttachableContext;
import ch_qos_logback.logback_core.ConsoleAppenderTest.CoreAppenderConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class LayoutWrappingEncoderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void xmlCharsetControlsBytesWrittenByLayoutWrappingEncoder() throws Exception {
        Path logFile = temporaryDirectory.resolve("encoded.log");
        AttachableContext context = new AttachableContext();
        context.putProperty("logFile", logFile.toString());
        CoreAppenderConfigurator configurator = new CoreAppenderConfigurator();
        configurator.setContext(context);

        try {
            configurator.doConfigure(new ByteArrayInputStream("""
                    <configuration>
                        <appender name="ENCODED" class="ch.qos.logback.core.FileAppender">
                            <file>${logFile}</file>
                            <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
                                <charset>UTF-16BE</charset>
                                <layout class="ch.qos.logback.core.layout.EchoLayout"/>
                            </encoder>
                        </appender>
                        <appender-ref ref="ENCODED"/>
                    </configuration>
                    """.getBytes(StandardCharsets.UTF_8)));

            FileAppender<?> appender = (FileAppender<?>) context.getAppender("ENCODED");
            LayoutWrappingEncoder<?> encoder = (LayoutWrappingEncoder<?>) appender.getEncoder();
            assertThat(appender.isStarted()).isTrue();
            assertThat(encoder.getCharset()).isEqualTo(StandardCharsets.UTF_16BE);
            context.getAppender("ENCODED").doAppend("caf\u00e9");
            context.detachAndStopAllAppenders();

            assertThat(Files.readAllBytes(logFile)).isEqualTo(
                    ("caf\u00e9" + System.lineSeparator()).getBytes(StandardCharsets.UTF_16BE));
        } finally {
            context.detachAndStopAllAppenders();
            context.stop();
        }
    }
}
