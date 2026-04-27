/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Hierarchy;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerFactory;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.OptionHandler;
import org.apache.log4j.spi.ThrowableRenderer;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DOMConfiguratorTest {

    @BeforeEach
    void setUp() {
        resetState();
    }

    @AfterEach
    void tearDown() {
        resetState();
    }

    @Test
    void configuresCustomLoggerLevelAppenderLayoutAndThrowableRendererFromXml() throws Exception {
        String loggerName = DOMConfiguratorTest.class.getName() + "." + System.nanoTime();
        Hierarchy repository = (Hierarchy) LogManager.getLoggerRepository();
        Document document = parseConfiguration("""
                <log4j:configuration xmlns:log4j="http://jakarta.apache.org/log4j/">
                  <appender name="TEST" class="%s">
                    <param name="Marker" value="configured-appender"/>
                    <layout class="%s">
                      <param name="Prefix" value="formatted:"/>
                    </layout>
                  </appender>
                  <logger name="%s" class="%s" additivity="false">
                    <level value="CUSTOM" class="%s"/>
                    <appender-ref ref="TEST"/>
                  </logger>
                  <throwableRenderer class="%s">
                    <param name="Label" value="rendered"/>
                  </throwableRenderer>
                </log4j:configuration>
                """.formatted(
                TrackingAppender.class.getName(),
                TrackingLayout.class.getName(),
                loggerName,
                TrackingLogger.class.getName(),
                TrackingLevel.class.getName(),
                TrackingThrowableRenderer.class.getName()));

        new DOMConfigurator().doConfigure(document.getDocumentElement(), repository);

        assertThat(TrackingLogger.getLoggerCallCount).isEqualTo(1);
        assertThat(TrackingLogger.lastRequestedName).isEqualTo(loggerName);
        assertThat(TrackingLevel.toLevelCallCount).isEqualTo(1);

        Logger configuredLogger = repository.getLogger(loggerName);
        assertThat(configuredLogger).isInstanceOf(TrackingLogger.class);
        assertThat(configuredLogger.getLevel()).isSameAs(TrackingLevel.CUSTOM);
        assertThat(configuredLogger.getAdditivity()).isFalse();
        assertThat(Collections.list(configuredLogger.getAllAppenders())).containsExactly(TrackingAppender.lastInstance);

        assertThat(TrackingAppender.lastInstance).isNotNull();
        assertThat(TrackingAppender.lastInstance.getName()).isEqualTo("TEST");
        assertThat(TrackingAppender.lastInstance.getMarker()).isEqualTo("configured-appender");
        assertThat(TrackingAppender.lastInstance.isActivated()).isTrue();

        assertThat(TrackingLayout.lastInstance).isNotNull();
        assertThat(TrackingLayout.lastInstance.getPrefix()).isEqualTo("formatted:");
        assertThat(TrackingLayout.lastInstance.isActivated()).isTrue();

        configuredLogger.log(TrackingLevel.CUSTOM, "configured message");
        assertThat(TrackingAppender.lastInstance.getMessages())
                .containsExactly("configured-appender|formatted:configured message");

        assertThat(repository.getThrowableRenderer()).isInstanceOf(TrackingThrowableRenderer.class);
        assertThat(TrackingThrowableRenderer.lastInstance).isNotNull();
        assertThat(TrackingThrowableRenderer.lastInstance.getLabel()).isEqualTo("rendered");
        assertThat(TrackingThrowableRenderer.lastInstance.isActivated()).isTrue();
        assertThat(TrackingThrowableRenderer.lastInstance.doRender(new IllegalStateException("boom")))
                .containsExactly("rendered:boom");
    }

    private static Document parseConfiguration(String xml) throws Exception {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        return documentBuilderFactory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static void resetState() {
        LogManager.resetConfiguration();
        TrackingLogger.reset();
        TrackingLevel.reset();
        TrackingAppender.reset();
        TrackingLayout.reset();
        TrackingThrowableRenderer.reset();
    }

    public static final class TrackingLogger extends Logger {
        private static final LoggerFactory FACTORY = new TrackingLoggerFactory();
        private static int getLoggerCallCount;
        private static String lastRequestedName;

        private TrackingLogger(String name) {
            super(name);
        }

        public static Logger getLogger(String name) {
            getLoggerCallCount++;
            lastRequestedName = name;
            return Logger.getLogger(name, FACTORY);
        }

        private static void reset() {
            getLoggerCallCount = 0;
            lastRequestedName = null;
        }
    }

    public static final class TrackingLevel extends Level {
        private static final long serialVersionUID = 1L;
        public static final TrackingLevel CUSTOM = new TrackingLevel(35000, "CUSTOM", 0);
        private static int toLevelCallCount;

        private TrackingLevel(int level, String levelStr, int syslogEquivalent) {
            super(level, levelStr, syslogEquivalent);
        }

        public static Level toLevel(String name) {
            toLevelCallCount++;
            if ("CUSTOM".equalsIgnoreCase(name)) {
                return CUSTOM;
            }
            return Level.DEBUG;
        }

        private static void reset() {
            toLevelCallCount = 0;
        }
    }

    public static final class TrackingAppender extends AppenderSkeleton {
        private static TrackingAppender lastInstance;

        private final List<String> messages = new ArrayList<>();
        private boolean activated;
        private String marker;

        public TrackingAppender() {
            lastInstance = this;
        }

        @Override
        public void activateOptions() {
            activated = true;
        }

        @Override
        protected void append(LoggingEvent event) {
            messages.add(marker + "|" + getLayout().format(event));
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean requiresLayout() {
            return true;
        }

        public List<String> getMessages() {
            return messages;
        }

        public String getMarker() {
            return marker;
        }

        public void setMarker(String marker) {
            this.marker = marker;
        }

        public boolean isActivated() {
            return activated;
        }

        private static void reset() {
            lastInstance = null;
        }
    }

    public static final class TrackingLayout extends Layout {
        private static TrackingLayout lastInstance;

        private boolean activated;
        private String prefix = "";

        public TrackingLayout() {
            lastInstance = this;
        }

        @Override
        public void activateOptions() {
            activated = true;
        }

        @Override
        public String format(LoggingEvent event) {
            return prefix + event.getRenderedMessage();
        }

        @Override
        public boolean ignoresThrowable() {
            return true;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public boolean isActivated() {
            return activated;
        }

        private static void reset() {
            lastInstance = null;
        }
    }

    public static final class TrackingThrowableRenderer implements ThrowableRenderer, OptionHandler {
        private static TrackingThrowableRenderer lastInstance;

        private boolean activated;
        private String label = "";

        public TrackingThrowableRenderer() {
            lastInstance = this;
        }

        @Override
        public void activateOptions() {
            activated = true;
        }

        @Override
        public String[] doRender(Throwable throwable) {
            return new String[]{label + ":" + throwable.getMessage()};
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public boolean isActivated() {
            return activated;
        }

        private static void reset() {
            lastInstance = null;
        }
    }

    private static final class TrackingLoggerFactory implements LoggerFactory {
        @Override
        public Logger makeNewLoggerInstance(String name) {
            return new TrackingLogger(name);
        }
    }
}
