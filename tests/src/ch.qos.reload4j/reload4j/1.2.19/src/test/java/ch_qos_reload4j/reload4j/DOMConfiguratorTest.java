/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Hierarchy;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.RootLogger;
import org.apache.log4j.spi.ThrowableRenderer;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class DOMConfiguratorTest {
    @BeforeEach
    void resetRecordingState() {
        RecordingAppender.lastInstance = null;
        RecordingLayout.lastInstance = null;
        RecordingThrowableRenderer.lastInstance = null;
        CustomLogger.lastLogger = null;
        CustomLogger.lastRequestedName = null;
        CustomLevel.lastRequestedLevel = null;
    }

    @Test
    void configuresCustomAppenderLayoutLoggerLevelAndThrowableRendererFromXml() throws Exception {
        Hierarchy repository = new Hierarchy(new RootLogger(Level.DEBUG));
        Document document = parseDocument("""
                <log4j:configuration xmlns:log4j="http://jakarta.apache.org/log4j/">
                  <appender name="recording" class="%s">
                    <layout class="%s">
                      <param name="Pattern" value="%%m"/>
                    </layout>
                  </appender>
                  <category name="reload4j.dom.custom" class="%s" additivity="false">
                    <level value="VERBOSE" class="%s"/>
                  </category>
                  <root>
                    <level value="INFO"/>
                    <appender-ref ref="recording"/>
                  </root>
                  <throwableRenderer class="%s"/>
                </log4j:configuration>
                """.formatted(
                        RecordingAppender.class.getName(),
                        RecordingLayout.class.getName(),
                        CustomLogger.class.getName(),
                        CustomLevel.class.getName(),
                        RecordingThrowableRenderer.class.getName()));

        new DOMConfigurator().doConfigure(document.getDocumentElement(), repository);

        Appender rootAppender = repository.getRootLogger().getAppender("recording");
        assertThat(rootAppender).isSameAs(RecordingAppender.lastInstance);
        assertThat(RecordingAppender.lastInstance.getLayout()).isSameAs(RecordingLayout.lastInstance);
        assertThat(RecordingLayout.lastInstance.pattern).isEqualTo("%m");

        assertThat(CustomLogger.lastRequestedName).isEqualTo("reload4j.dom.custom");
        assertThat(CustomLogger.lastLogger.getLevel()).isSameAs(CustomLevel.VERBOSE);
        assertThat(CustomLogger.lastLogger.getAdditivity()).isFalse();
        assertThat(CustomLevel.lastRequestedLevel).isEqualTo("VERBOSE");

        ThrowableRenderer configuredRenderer = ((ThrowableRendererSupport) repository).getThrowableRenderer();
        assertThat(configuredRenderer).isSameAs(RecordingThrowableRenderer.lastInstance);
    }

    private static Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    public static final class RecordingAppender extends AppenderSkeleton {
        private static RecordingAppender lastInstance;

        public RecordingAppender() {
            lastInstance = this;
        }

        @Override
        protected void append(LoggingEvent event) {
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean requiresLayout() {
            return true;
        }
    }

    public static final class RecordingLayout extends Layout {
        private static RecordingLayout lastInstance;
        private String pattern;

        public RecordingLayout() {
            lastInstance = this;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public String format(LoggingEvent event) {
            return event.getRenderedMessage();
        }

        @Override
        public boolean ignoresThrowable() {
            return true;
        }

        @Override
        public void activateOptions() {
        }
    }

    public static final class CustomLogger extends Logger {
        private static CustomLogger lastLogger;
        private static String lastRequestedName;

        private CustomLogger(String name) {
            super(name);
        }

        public static Logger getLogger(String name) {
            lastRequestedName = name;
            lastLogger = new CustomLogger(name);
            return lastLogger;
        }
    }

    public static final class CustomLevel extends Level {
        private static final Level VERBOSE = new CustomLevel(4500, "VERBOSE", 7);
        private static String lastRequestedLevel;

        private CustomLevel(int level, String levelStr, int syslogEquivalent) {
            super(level, levelStr, syslogEquivalent);
        }

        public static Level toLevel(String levelName) {
            lastRequestedLevel = levelName;
            return VERBOSE;
        }
    }

    public static final class RecordingThrowableRenderer implements ThrowableRenderer {
        private static RecordingThrowableRenderer lastInstance;

        public RecordingThrowableRenderer() {
            lastInstance = this;
        }

        @Override
        public String[] doRender(Throwable throwable) {
            return new String[] {throwable.toString() };
        }
    }
}
