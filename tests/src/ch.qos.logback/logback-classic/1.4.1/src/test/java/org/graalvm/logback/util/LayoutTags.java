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

    public static final String ECHO_TAG = "<layout class=\"ch.qos.logback.core.layout.EchoLayout\" />";

    public static final String HTML_TAG = """
            <layout class="ch.qos.logback.classic.html.HTMLLayout">
                <pattern>%msg</pattern>
                <cssBuilder class="ch.qos.logback.classic.html.UrlCssBuilder"><url>test-url</url></cssBuilder>
            </layout>""";

    public static final String TTLL_TAG = "<layout class=\"ch.qos.logback.classic.layout.TTLLLayout\" />";

    public static final String XML_TAG = """
            <layout class="ch.qos.logback.classic.log4j.XMLLayout">
                <locationInfo>true</locationInfo>
                <properties>true</properties>
            </layout>""";
}
