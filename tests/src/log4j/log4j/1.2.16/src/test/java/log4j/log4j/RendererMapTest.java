/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.or.ObjectRenderer;
import org.apache.log4j.or.RendererMap;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.RendererSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RendererMapTest {

    @BeforeEach
    void setUp() {
        resetState();
    }

    @AfterEach
    void tearDown() {
        resetState();
    }

    @Test
    void addRendererRegistersRendererForMatchingMessages() {
        RendererSupport rendererSupport = (RendererSupport) LogManager.getLoggerRepository();

        RendererMap.addRenderer(
                rendererSupport,
                RenderedMessage.class.getName(),
                TrackingRenderer.class.getName());

        String rendered = rendererSupport.getRendererMap().findAndRender(new RenderedMessage("payload"));

        assertThat(rendered).isEqualTo("rendered:payload");
        assertThat(rendererSupport.getRendererMap().get(new RenderedMessage("next")))
                .isInstanceOf(TrackingRenderer.class);
    }

    @Test
    void propertyConfiguratorRegistersRendererForLoggedMessages() {
        String loggerName = RendererMapTest.class.getName() + "." + System.nanoTime();
        Properties properties = new Properties();
        properties.setProperty("log4j.rootLogger", "ERROR");
        properties.setProperty("log4j.logger." + loggerName, "INFO,TEST");
        properties.setProperty("log4j.additivity." + loggerName, "false");
        properties.setProperty("log4j.appender.TEST", TrackingAppender.class.getName());
        properties.setProperty("log4j.renderer." + RenderedMessage.class.getName(), TrackingRenderer.class.getName());

        PropertyConfigurator.configure(properties);

        Logger logger = Logger.getLogger(loggerName);
        logger.info(new RenderedMessage("from-logger"));

        assertThat(TrackingAppender.renderedMessages).containsExactly("rendered:from-logger");
    }

    private static void resetState() {
        LogManager.resetConfiguration();
        TrackingAppender.reset();
    }

    public static final class RenderedMessage {
        private final String value;

        public RenderedMessage(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class TrackingRenderer implements ObjectRenderer {
        @Override
        public String doRender(Object object) {
            return "rendered:" + ((RenderedMessage) object).getValue();
        }
    }

    public static final class TrackingAppender extends AppenderSkeleton {
        private static final List<String> renderedMessages = new ArrayList<>();

        @Override
        protected void append(LoggingEvent event) {
            renderedMessages.add(event.getRenderedMessage());
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }

        private static void reset() {
            renderedMessages.clear();
        }
    }
}
