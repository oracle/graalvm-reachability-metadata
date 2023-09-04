/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback.util;

public class LayoutTags {

    public static final String CONFIG_TAG = """
            <configuration>
                <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                    <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
                        %s
                    </encoder>
                </appender>
                <root>
                    <appender-ref ref="STDOUT"/>
                </root>
            </configuration>
            """;

    public static final String PATTERN_TAG = """
            <layout class="ch.qos.logback.classic.PatternLayout">
                <pattern>%msg</pattern>
                <outputPatternAsHeader>true</outputPatternAsHeader>
            </layout>""";

    public static final String XML_TAG = """
            <layout class="ch.qos.logback.classic.log4j.XMLLayout">
                <locationInfo>true</locationInfo>
                <properties>true</properties>
            </layout>""";
}
