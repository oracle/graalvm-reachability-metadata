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
import org.junit.jupiter.api.Test;

public class PropertySetterTest {

    @Test
    void setPropertyConvertsValueAndInvokesDiscoveredSetter() {
        ConfigBean configBean = new ConfigBean();
        PropertySetter propertySetter = newPropertySetter(configBean);

        propertySetter.setProperty("message", "configured");

        assertThat(configBean.getMessage()).isEqualTo("configured");
    }

    @Test
    void addComplexPropertyInvokesDiscoveredAdder() {
        ConfigBean configBean = new ConfigBean();
        PropertySetter propertySetter = newPropertySetter(configBean);
        NestedComponent nestedComponent = new NestedComponent("appender-child");

        propertySetter.addComplexProperty("nestedComponent", nestedComponent);

        assertThat(configBean.getNestedComponent()).isSameAs(nestedComponent);
    }

    @Test
    void implicitRulesResolveConcreteDefaultConstructiblePropertyType() {
        ConfigBean configBean = new ConfigBean();
        PropertySetter propertySetter = newPropertySetter(configBean);
        DefaultNestedComponentRegistry registry = new DefaultNestedComponentRegistry();

        Class<?> componentType = propertySetter.getClassNameViaImplicitRules(
                "instantiableComponent",
                AggregationType.AS_COMPLEX_PROPERTY,
                registry);

        assertThat(componentType).isEqualTo(InstantiableComponent.class);
    }

    private PropertySetter newPropertySetter(Object target) {
        return new PropertySetter(new BeanDescriptionCache(null), target);
    }

    public static final class ConfigBean {
        private String message;
        private NestedComponent nestedComponent;

        public void setMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void addNestedComponent(NestedComponent nestedComponent) {
            this.nestedComponent = nestedComponent;
        }

        public NestedComponent getNestedComponent() {
            return nestedComponent;
        }

        public void setInstantiableComponent(InstantiableComponent instantiableComponent) {
        }
    }

    public static final class NestedComponent {
        private final String name;

        public NestedComponent(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final class InstantiableComponent {

        public InstantiableComponent() {
        }
    }
}
