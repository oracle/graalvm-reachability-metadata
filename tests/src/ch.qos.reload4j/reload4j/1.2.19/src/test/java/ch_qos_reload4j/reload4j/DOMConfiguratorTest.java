/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Appender;
import org.apache.log4j.DefaultThrowableRenderer;
import org.apache.log4j.Hierarchy;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.RootLogger;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.apache.log4j.varia.NullAppender;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class DOMConfiguratorTest {
    private static final String APPENDER_NAME = "memory";
    private static final String CATEGORY_NAME = "reload4j.dom.category";
    private static final String CONVERSION_PATTERN = "%p %c - %m%n";
    private static final String CUSTOM_LEVEL_NAME = "CUSTOM_DYNAMIC_LEVEL";
    private static final CustomLevel CUSTOM_LEVEL = new CustomLevel(35_000, CUSTOM_LEVEL_NAME, 7);

    @BeforeEach
    void resetCustomConfigurationTargets() {
        CustomLogger.lastLogger = null;
        CustomLevel.lastRequestedLevel = null;
    }

    @Test
    void configuresAppenderLayoutThrowableRendererCustomLoggerAndCustomLevelFromXml() throws Exception {
        Hierarchy repository = new Hierarchy(new RootLogger(Level.DEBUG));
        Document document = parseXmlConfiguration();

        new DOMConfigurator().doConfigure(document.getDocumentElement(), repository);

        Appender appender = repository.getRootLogger().getAppender(APPENDER_NAME);
        assertThat(appender).isInstanceOf(NullAppender.class);
        assertThat(appender.getLayout()).isInstanceOf(PatternLayout.class);
        assertThat(((PatternLayout) appender.getLayout()).getConversionPattern()).isEqualTo(CONVERSION_PATTERN);
        assertThat(repository.getRootLogger().getLevel()).isSameAs(CUSTOM_LEVEL);
        assertThat(((ThrowableRendererSupport) repository).getThrowableRenderer())
                .isInstanceOf(DefaultThrowableRenderer.class);
        assertThat(CustomLogger.lastLogger).isNotNull();
        assertThat(CustomLogger.lastLogger.getName()).isEqualTo(CATEGORY_NAME);
        assertThat(CustomLogger.lastLogger.getLevel()).isSameAs(CUSTOM_LEVEL);
        assertThat(CustomLevel.lastRequestedLevel).isEqualTo(CUSTOM_LEVEL_NAME);
    }

    private static Document parseXmlConfiguration() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xmlConfiguration())));
    }

    private static String xmlConfiguration() {
        return """
                <log4j:configuration xmlns:log4j="http://jakarta.apache.org/log4j/">
                  <appender name="%s" class="org.apache.log4j.varia.NullAppender">
                    <layout class="org.apache.log4j.PatternLayout">
                      <param name="ConversionPattern" value="%s"/>
                    </layout>
                  </appender>
                  <logger name="%s" class="%s" additivity="false">
                    <level value="%s" class="%s"/>
                    <appender-ref ref="%s"/>
                  </logger>
                  <root>
                    <level value="%s" class="%s"/>
                    <appender-ref ref="%s"/>
                  </root>
                  <throwableRenderer class="org.apache.log4j.DefaultThrowableRenderer"/>
                </log4j:configuration>
                """.formatted(
                        APPENDER_NAME,
                        CONVERSION_PATTERN,
                        CATEGORY_NAME,
                        CustomLogger.class.getName(),
                        CUSTOM_LEVEL_NAME,
                        CustomLevel.class.getName(),
                        APPENDER_NAME,
                        CUSTOM_LEVEL_NAME,
                        CustomLevel.class.getName(),
                        APPENDER_NAME);
    }

    public static final class CustomLogger extends Logger {
        private static CustomLogger lastLogger;

        public CustomLogger(String name) {
            super(name);
        }

        public static Logger getLogger(String name) {
            lastLogger = new CustomLogger(name);
            return lastLogger;
        }
    }

    public static final class CustomLevel extends Level {
        private static String lastRequestedLevel;

        protected CustomLevel(int level, String levelStr, int syslogEquivalent) {
            super(level, levelStr, syslogEquivalent);
        }

        public static Level toLevel(String levelName) {
            lastRequestedLevel = levelName;
            return CUSTOM_LEVEL;
        }
    }
}
