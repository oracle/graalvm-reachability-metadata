/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import org.apache.log4j.LogManager;
import org.apache.log4j.or.ObjectRenderer;
import org.apache.log4j.or.RendererMap;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.RendererSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RendererMapTest {

    @BeforeEach
    @AfterEach
    void resetConfiguration() {
        LogManager.resetConfiguration();
    }

    @Test
    void addsRendererByClassNameAndUsesItForMatchingMessages() {
        LoggerRepository repository = LogManager.getLoggerRepository();
        assertThat(repository).isInstanceOf(RendererSupport.class);

        RendererSupport rendererSupport = (RendererSupport) repository;
        RendererMap.addRenderer(
                rendererSupport,
                RenderedMessage.class.getName(),
                TrackingRenderer.class.getName());

        String rendered = rendererSupport.getRendererMap().findAndRender(new RenderedMessage("payload"));

        assertThat(rendered).isEqualTo("rendered:payload");
        assertThat(rendererSupport.getRendererMap().get(new RenderedMessage("second")))
                .isInstanceOf(TrackingRenderer.class);
    }

    public static final class RenderedMessage {
        private final String value;

        private RenderedMessage(String value) {
            this.value = value;
        }
    }

    public static final class TrackingRenderer implements ObjectRenderer {
        @Override
        public String doRender(Object object) {
            RenderedMessage renderedMessage = (RenderedMessage) object;
            return "rendered:" + renderedMessage.value;
        }
    }
}
