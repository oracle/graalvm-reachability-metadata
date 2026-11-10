/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch_qos_logback.logback_core.ConsoleAppenderTest.AttachableContext;
import ch_qos_logback.logback_core.ConsoleAppenderTest.CoreAppenderConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class SizeAndTimeBasedRollingPolicyTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void xmlCreatesSizeAndTimePolicyAndRollsAtConfiguredMaximumFileSize() throws Exception {
        Path logFile = temporaryDirectory.resolve("active.log");
        AttachableContext context = new AttachableContext();
        context.putProperty("logFile", logFile.toString());
        context.putProperty("archivePattern",
                temporaryDirectory.resolve("archive-%d{yyyy-MM-dd, UTC}.%i.log").toString());
        CoreAppenderConfigurator configurator = new CoreAppenderConfigurator();
        configurator.setContext(context);

        try {
            configurator.doConfigure(new ByteArrayInputStream("""
                    <configuration>
                        <appender name="ROLLING" class="ch.qos.logback.core.rolling.RollingFileAppender">
                            <file>${logFile}</file>
                            <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
                                <fileNamePattern>${archivePattern}</fileNamePattern>
                                <maxFileSize>1KB</maxFileSize>
                            </rollingPolicy>
                            <encoder class="ch.qos.logback.core.encoder.EchoEncoder"/>
                        </appender>
                        <appender-ref ref="ROLLING"/>
                    </configuration>
                    """.getBytes(StandardCharsets.UTF_8)));

            RollingFileAppender<?> appender = (RollingFileAppender<?>) context.getAppender("ROLLING");
            assertThat(appender.isStarted()).isTrue();
            assertThat(appender.getRollingPolicy()).isInstanceOf(SizeAndTimeBasedRollingPolicy.class);
            SizeAndTimeBasedRollingPolicy<?> policy = (SizeAndTimeBasedRollingPolicy<?>) appender.getRollingPolicy();
            assertThat(policy.isStarted()).isTrue();

            String largeEvent = "x".repeat(1024);
            context.getAppender("ROLLING").doAppend(largeEvent);
            context.getAppender("ROLLING").doAppend("next file");
            Path archiveFile = Path.of(policy.getTimeBasedFileNamingAndTriggeringPolicy().getElapsedPeriodsFileName());
            context.detachAndStopAllAppenders();

            assertThat(Files.readString(archiveFile)).isEqualTo(largeEvent + System.lineSeparator());
            assertThat(Files.readString(logFile)).isEqualTo("next file" + System.lineSeparator());
        } finally {
            context.detachAndStopAllAppenders();
            context.stop();
        }
    }
}
