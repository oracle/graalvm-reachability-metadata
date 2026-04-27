/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.apache.log4j.config.PropertySetter;
import org.apache.log4j.spi.OptionHandler;
import org.junit.jupiter.api.Test;

public class PropertySetterTest {
    @Test
    void configuresNestedOptionHandlerPropertyFromProperties() {
        PropertyTarget target = new PropertyTarget();
        Properties properties = new Properties();
        properties.setProperty("target.handler", ConfiguredOptionHandler.class.getName());

        PropertySetter.setProperties(target, properties, "target.");

        assertThat(target.getHandler()).isInstanceOf(ConfiguredOptionHandler.class);
        ConfiguredOptionHandler handler = (ConfiguredOptionHandler) target.getHandler();
        assertThat(handler.isActivated()).isTrue();
    }

    public static final class PropertyTarget {
        private OptionHandler handler;

        public OptionHandler getHandler() {
            return handler;
        }

        public void setHandler(OptionHandler handler) {
            this.handler = handler;
        }
    }

    public static final class ConfiguredOptionHandler implements OptionHandler {
        private boolean activated;

        public ConfiguredOptionHandler() {
        }

        @Override
        public void activateOptions() {
            activated = true;
        }

        public boolean isActivated() {
            return activated;
        }
    }
}
