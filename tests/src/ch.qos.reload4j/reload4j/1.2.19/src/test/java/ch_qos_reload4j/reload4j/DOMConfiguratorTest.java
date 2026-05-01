/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableRenderer;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

public class DOMConfiguratorTest {
    private static final String LOGGER_NAME = "dom.configurator.category";
    private static final String CUSTOM_LEVEL = "DOM_CONFIGURATOR_LEVEL";

    @AfterEach
    void resetRepository() {
        LogManager.resetConfiguration();
        CollectingAppender.lastAppender = null;
        LoggerProvider.requestedLoggerName = null;
        LevelProvider.requestedLevelName = null;
        RenderingThrowableRenderer.lastRenderer = null;
    }

    @Test
    void configuresCustomAppenderLayoutLoggerLevelAndThrowableRendererFromXml() throws Exception {
        String xml = """
                <log4j:configuration xmlns:log4j="http://jakarta.apache.org/log4j/" reset="true">
                  <appender name="CAPTURE" class="%s">
                    <layout class="%s"/>
                  </appender>
                  <throwableRenderer class="%s"/>
                  <logger name="%s" class="%s" additivity="false">
                    <level value="%s" class="%s"/>
                    <appender-ref ref="CAPTURE"/>
                  </logger>
                </log4j:configuration>
                """.formatted(
                CollectingAppender.class.getName(),
                MarkerLayout.class.getName(),
                RenderingThrowableRenderer.class.getName(),
                LOGGER_NAME,
                LoggerProvider.class.getName(),
                CUSTOM_LEVEL,
                LevelProvider.class.getName());
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        new DOMConfigurator().doConfigure(document.getDocumentElement(), LogManager.getLoggerRepository());

        Logger configuredLogger = Logger.getLogger(LOGGER_NAME);
        configuredLogger.error("message rendered by configured layout");
        ThrowableRendererSupport rendererSupport = (ThrowableRendererSupport) LogManager.getLoggerRepository();
        ThrowableRenderer renderer = rendererSupport.getThrowableRenderer();

        assertThat(LoggerProvider.requestedLoggerName).isEqualTo(LOGGER_NAME);
        assertThat(LevelProvider.requestedLevelName).isEqualTo(CUSTOM_LEVEL);
        assertThat(configuredLogger.getLevel()).isSameAs(Level.ERROR);
        assertThat(CollectingAppender.lastAppender).isNotNull();
        assertThat(CollectingAppender.lastAppender.lastRenderedMessage)
                .isEqualTo("dom-layout:message rendered by configured layout");
        assertThat(renderer).isNotNull().isSameAs(RenderingThrowableRenderer.lastRenderer);
        assertThat(renderer.doRender(new IllegalStateException("expected throwable")))
                .containsExactly("rendered:expected throwable");
    }

    public static class LoggerProvider {
        static String requestedLoggerName;

        public static Logger getLogger(String name) {
            requestedLoggerName = name;
            return Logger.getLogger(name);
        }
    }

    public static class LevelProvider {
        static String requestedLevelName;

        public static Level toLevel(String name) {
            requestedLevelName = name;
            return Level.ERROR;
        }
    }

    public static class CollectingAppender extends AppenderSkeleton {
        static CollectingAppender lastAppender;
        String lastRenderedMessage;

        public CollectingAppender() {
            lastAppender = this;
        }

        @Override
        protected void append(LoggingEvent event) {
            lastRenderedMessage = getLayout().format(event);
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

    public static class MarkerLayout extends Layout {
        public MarkerLayout() {
        }

        @Override
        public String format(LoggingEvent event) {
            return "dom-layout:" + event.getRenderedMessage();
        }

        @Override
        public boolean ignoresThrowable() {
            return false;
        }

        @Override
        public void activateOptions() {
        }
    }

    public static class RenderingThrowableRenderer implements ThrowableRenderer {
        static RenderingThrowableRenderer lastRenderer;

        public RenderingThrowableRenderer() {
            lastRenderer = this;
        }

        @Override
        public String[] doRender(Throwable throwable) {
            return new String[] { "rendered:" + throwable.getMessage() };
        }
    }
}
