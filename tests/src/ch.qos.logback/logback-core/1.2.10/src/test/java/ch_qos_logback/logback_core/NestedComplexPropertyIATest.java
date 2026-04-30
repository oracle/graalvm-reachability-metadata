/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.encoder.EchoEncoder;
import ch.qos.logback.core.joran.JoranConfiguratorBase;
import ch.qos.logback.core.joran.action.ActionConst;
import ch.qos.logback.core.joran.spi.JoranException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class NestedComplexPropertyIATest {

    @Test
    void configuresNestedEncoderFromClassAttribute() throws JoranException {
        ContextBase context = new ContextBase();
        TestConfigurator configurator = new TestConfigurator();
        configurator.setContext(context);

        configurator.doConfigure(new ByteArrayInputStream(configurationXml().getBytes(StandardCharsets.UTF_8)));

        Map<?, ?> appenderBag = (Map<?, ?>) configurator.getInterpretationContext()
                .getObjectMap()
                .get(ActionConst.APPENDER_BAG);
        Object appenderObject = appenderBag.get("CONSOLE");

        assertThat(appenderObject).isInstanceOf(ConsoleAppender.class);
        ConsoleAppender<?> appender = (ConsoleAppender<?>) appenderObject;
        assertThat(appender.getEncoder()).isInstanceOf(EchoEncoder.class);
        assertThat(appender.getEncoder().getContext()).isSameAs(context);
    }

    private static String configurationXml() {
        return """
                <configuration>
                  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
                    <encoder class="ch.qos.logback.core.encoder.EchoEncoder"/>
                  </appender>
                </configuration>
                """;
    }

    public static final class TestConfigurator extends JoranConfiguratorBase<Object> {
    }
}
