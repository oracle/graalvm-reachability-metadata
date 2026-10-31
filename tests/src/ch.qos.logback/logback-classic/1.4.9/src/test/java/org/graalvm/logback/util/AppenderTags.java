/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback.util;

public class AppenderTags {

    public static final String CONFIG_TAG = """
            <configuration>
            %s
                <root>
                    <appender-ref ref="FILE"/>
                </root>
            </configuration>
            """;

    public static final String FILE_TAG = """
                <appender name="FILE" class="ch.qos.logback.core.FileAppender">
                    <file>%s</file>
                    <append>true</append>
                    <prudent>false</prudent>
                    <encoder>
                        <pattern>%%msg</pattern>
                    </encoder>
                    <immediateFlush>true</immediateFlush>
                </appender>
            """;

    public static final String ROLLING_FILE_TAG = """
                <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                    <file>%s</file>
                    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
                        <fileNamePattern>rolling-log-%%d{yyyy-MM-dd}.%%i.txt</fileNamePattern>
                        <maxFileSize>100MB</maxFileSize>
                        <maxHistory>60</maxHistory>
                        <totalSizeCap>20GB</totalSizeCap>
                        <cleanHistoryOnStart>false</cleanHistoryOnStart>
                    </rollingPolicy>
                    <triggeringPolicy class="ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy">
                        <maxFileSize>100MB</maxFileSize>
                    </triggeringPolicy>
                    <append>true</append>
                    <prudent>false</prudent>
                    <encoder>
                        <pattern>%%msg</pattern>
                    </encoder>
                    <immediateFlush>true</immediateFlush>
                </appender>
            """;

}
