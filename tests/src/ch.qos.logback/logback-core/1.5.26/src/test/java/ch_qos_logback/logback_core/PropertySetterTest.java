/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.joran.util.PropertySetter;
import ch.qos.logback.core.joran.util.beans.BeanDescriptionCache;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertySetterTest {

    @Test
    void setsSimplePropertyAndAddsBasicCollectionValue() {
        ConfigurableTarget target = new ConfigurableTarget();
        PropertySetter propertySetter = new PropertySetter(new BeanDescriptionCache(new ContextBase()), target);

        propertySetter.setProperty("name", "primary");
        propertySetter.addBasicProperty("port", "1042");

        assertThat(target.getName()).isEqualTo("primary");
        assertThat(target.getPortTotal()).isEqualTo(1042);
    }

    public static class ConfigurableTarget {
        private String name;
        private int portTotal;

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void addPort(int port) {
            portTotal += port;
        }

        public int getPortTotal() {
            return portTotal;
        }
    }
}
