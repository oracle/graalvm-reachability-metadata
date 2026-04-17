/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.joran.spi.DefaultNestedComponentRegistry;
import ch.qos.logback.core.joran.util.PropertySetter;
import ch.qos.logback.core.joran.util.beans.BeanDescriptionCache;
import ch.qos.logback.core.util.AggregationType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PropertySetterTest {

    @Test
    void invokesSetterWhenAssigningAStringProperty() {
        PropertyTarget target = new PropertyTarget();
        PropertySetter propertySetter = new PropertySetter(new BeanDescriptionCache(null), target);

        propertySetter.setProperty("name", "test-name");

        assertThat(target.getName()).isEqualTo("test-name");
    }

    @Test
    void invokesAdderWhenAddingAComplexProperty() {
        PropertyTarget target = new PropertyTarget();
        PropertySetter propertySetter = new PropertySetter(new BeanDescriptionCache(null), target);
        NestedComponent component = new NestedComponent();

        propertySetter.addComplexProperty("component", component);

        assertThat(target.getComponents()).containsExactly(component);
    }

    @Test
    void resolvesConcreteNestedComponentTypeViaImplicitRules() {
        PropertySetter propertySetter = new PropertySetter(new BeanDescriptionCache(null), new PropertyTarget());

        Class<?> componentType = propertySetter.getClassNameViaImplicitRules(
                "component",
                AggregationType.AS_COMPLEX_PROPERTY,
                new DefaultNestedComponentRegistry());

        assertThat(componentType).isEqualTo(NestedComponent.class);
    }

    public static final class PropertyTarget {

        private String name;
        private NestedComponent component;
        private final List<NestedComponent> components = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public NestedComponent getComponent() {
            return component;
        }

        public void setComponent(NestedComponent component) {
            this.component = component;
        }

        public List<NestedComponent> getComponents() {
            return components;
        }

        public void addComponent(NestedComponent component) {
            components.add(component);
        }
    }

    public static final class NestedComponent {

        public NestedComponent() {
        }
    }
}
