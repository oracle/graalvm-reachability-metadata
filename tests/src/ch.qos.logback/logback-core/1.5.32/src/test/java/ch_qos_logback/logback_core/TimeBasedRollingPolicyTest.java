/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch_qos_logback.logback_core.ConsoleAppenderTest.AttachableContext;
import ch_qos_logback.logback_core.ConsoleAppenderTest.CoreAppenderConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

public class TimeBasedRollingPolicyTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void xmlRetentionOptionsCleanHistoryAndEnforceTotalSizeCapOnStartup() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Path recentArchive = temporaryDirectory.resolve("archive-" + today.minusDays(1) + ".log");
        Path overCapArchive = temporaryDirectory.resolve("archive-" + today.minusDays(2) + ".log");
        Path expiredArchive = temporaryDirectory.resolve("archive-" + today.minusDays(5) + ".log");
        Files.writeString(recentArchive, "a".repeat(700));
        Files.writeString(overCapArchive, "b".repeat(700));
        Files.writeString(expiredArchive, "expired");
        Path logFile = temporaryDirectory.resolve("active.log");
        AttachableContext context = new AttachableContext();
        context.putProperty("logFile", logFile.toString());
        context.putProperty("archivePattern",
                temporaryDirectory.resolve("archive-%d{yyyy-MM-dd, UTC}.log").toString());
        CoreAppenderConfigurator configurator = new CoreAppenderConfigurator();
        configurator.setContext(context);

        try {
            configurator.doConfigure(new ByteArrayInputStream("""
                    <configuration>
                        <appender name="HISTORY" class="ch.qos.logback.core.rolling.RollingFileAppender">
                            <file>${logFile}</file>
                            <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                                <fileNamePattern>${archivePattern}</fileNamePattern>
                                <cleanHistoryOnStart>true</cleanHistoryOnStart>
                                <maxHistory>3</maxHistory>
                                <totalSizeCap>1KB</totalSizeCap>
                            </rollingPolicy>
                            <encoder class="ch.qos.logback.core.encoder.EchoEncoder"/>
                        </appender>
                        <appender-ref ref="HISTORY"/>
                    </configuration>
                    """.getBytes(StandardCharsets.UTF_8)));

            RollingFileAppender<?> appender = (RollingFileAppender<?>) context.getAppender("HISTORY");
            TimeBasedRollingPolicy<?> policy = (TimeBasedRollingPolicy<?>) appender.getRollingPolicy();
            assertThat(appender.isStarted()).isTrue();
            assertThat(policy.isStarted()).isTrue();
            assertThat(policy.isCleanHistoryOnStart()).isTrue();
            assertThat(policy.getMaxHistory()).isEqualTo(3);
            context.getAppender("HISTORY").doAppend("current event");
            // Stopping the policy waits for its asynchronous startup cleanup.
            context.detachAndStopAllAppenders();

            assertThat(recentArchive).exists();
            assertThat(overCapArchive).doesNotExist();
            assertThat(expiredArchive).doesNotExist();
            assertThat(Files.readString(logFile)).isEqualTo("current event" + System.lineSeparator());
        } finally {
            context.detachAndStopAllAppenders();
            context.stop();
        }
    }
}
