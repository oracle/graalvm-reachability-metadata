/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.config.PropertySetter;
import org.apache.log4j.spi.OptionHandler;
import org.junit.jupiter.api.Test;

public class PropertySetterTest {

    @Test
    void invokesBeanSetterWithConvertedPropertyValues() {
        SimpleTarget target = new SimpleTarget();
        PropertySetter propertySetter = new PropertySetter(target);

        propertySetter.setProperty("name", "configured-name");
        propertySetter.setProperty("count", "7");
        propertySetter.setProperty("enabled", "true");
        propertySetter.setProperty("threshold", "INFO");

        assertThat(target.getName()).isEqualTo("configured-name");
        assertThat(target.getCount()).isEqualTo(7);
        assertThat(target.isEnabled()).isTrue();
        assertThat(target.getThreshold()).isEqualTo(Level.INFO);
    }

    @Test
    void instantiatesConfiguresAndAssignsNestedOptionHandlerFromProperties() {
        String prefix = "log4j.target.";
        Properties properties = new Properties();
        properties.setProperty(prefix + "handler", NestedOptionHandler.class.getName());
        properties.setProperty(prefix + "handler.name", "nested-name");

        OptionHandlerTarget target = new OptionHandlerTarget();

        PropertySetter.setProperties(target, properties, prefix);

        assertThat(target.getHandler()).isInstanceOf(NestedOptionHandler.class);
        NestedOptionHandler handler = (NestedOptionHandler) target.getHandler();
        assertThat(handler.getName()).isEqualTo("nested-name");
        assertThat(handler.isActivated()).isTrue();
    }

    public static final class SimpleTarget {
        private String name;
        private int count;
        private boolean enabled;
        private Level threshold;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Level getThreshold() {
            return threshold;
        }

        public void setThreshold(Level threshold) {
            this.threshold = threshold;
        }
    }

    public static final class OptionHandlerTarget {
        private OptionHandler handler;

        public OptionHandler getHandler() {
            return handler;
        }

        public void setHandler(OptionHandler handler) {
            this.handler = handler;
        }
    }

    public static final class NestedOptionHandler implements OptionHandler {
        private String name;
        private boolean activated;

        public NestedOptionHandler() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isActivated() {
            return activated;
        }

        @Override
        public void activateOptions() {
            activated = true;
        }
    }
}
