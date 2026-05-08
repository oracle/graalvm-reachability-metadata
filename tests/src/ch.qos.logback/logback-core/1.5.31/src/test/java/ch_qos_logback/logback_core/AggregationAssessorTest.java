/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.joran.spi.DefaultNestedComponentRegistry;
import ch.qos.logback.core.joran.util.AggregationAssessor;
import ch.qos.logback.core.joran.util.beans.BeanDescriptionCache;
import ch.qos.logback.core.util.AggregationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregationAssessorTest {

    @Test
    void infersDefaultComponentClassFromInstantiableConcreteSetterType() {
        BeanDescriptionCache beanDescriptionCache = new BeanDescriptionCache(new ContextBase());
        AggregationAssessor assessor = new AggregationAssessor(beanDescriptionCache, ConfigurableContainer.class);
        DefaultNestedComponentRegistry registry = new DefaultNestedComponentRegistry();

        AggregationType aggregationType = assessor.computeAggregationType("component");
        Class<?> componentClass = assessor.getClassNameViaImplicitRules("component", aggregationType, registry);

        assertThat(aggregationType).isEqualTo(AggregationType.AS_COMPLEX_PROPERTY);
        assertThat(componentClass).isEqualTo(ConcreteComponent.class);
    }

    public static class ConfigurableContainer {
        private ConcreteComponent component;

        public void setComponent(ConcreteComponent component) {
            this.component = component;
        }

        public ConcreteComponent getComponent() {
            return component;
        }
    }

    public static class ConcreteComponent {
        public ConcreteComponent() {
        }
    }
}
