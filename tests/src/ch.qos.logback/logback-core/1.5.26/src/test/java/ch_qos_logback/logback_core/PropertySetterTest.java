/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.joran.util.PropertySetter;
import ch.qos.logback.core.joran.util.beans.BeanDescriptionCache;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PropertySetterTest {

    @Test
    void setPropertyInvokesBeanSetter() {
        PropertyTarget target = new PropertyTarget();
        PropertySetter propertySetter = new PropertySetter(new BeanDescriptionCache(null), target);

        propertySetter.setProperty("name", "configured-name");

        assertThat(target.getName()).isEqualTo("configured-name");
    }

    @Test
    void addComplexPropertyInvokesBeanAdder() {
        PropertyTarget target = new PropertyTarget();
        PropertySetter propertySetter = new PropertySetter(new BeanDescriptionCache(null), target);
        NestedComponent component = new NestedComponent();

        propertySetter.addComplexProperty("component", component);

        assertThat(target.getComponents()).containsExactly(component);
    }

    public static final class PropertyTarget {

        private String name;
        private final List<NestedComponent> components = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<NestedComponent> getComponents() {
            return components;
        }

        public void addComponent(NestedComponent component) {
            components.add(component);
        }
    }

    public static final class NestedComponent {
    }
}
