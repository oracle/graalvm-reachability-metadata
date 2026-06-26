/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sonatype.guice.bean.reflect.BeanProperties;
import org.sonatype.guice.bean.reflect.BeanProperty;

public class BeanPropertySetterTest {
    @Test
    void setInvokesDiscoveredPrivateSetter() {
        SetterBackedBean bean = new SetterBackedBean();
        BeanProperty<Object> property = propertyNamed(SetterBackedBean.class, "configured");

        property.set(bean, "configured-value");

        assertThat(property.getName()).isEqualTo("configured");
        assertThat(bean.configuredValue()).isEqualTo("configured-value");
        assertThat(bean.setterInvocationCount()).isEqualTo(1);
    }

    private static BeanProperty<Object> propertyNamed(Class<?> beanType, String name) {
        for (BeanProperty<Object> property : new BeanProperties(beanType)) {
            if (name.equals(property.getName())) {
                return property;
            }
        }
        throw new AssertionError("No bean property named " + name);
    }

    private static final class SetterBackedBean {
        private String configuredValue;

        private int setterInvocationCount;

        private void setConfigured(String configuredValue) {
            this.configuredValue = configuredValue;
            setterInvocationCount++;
        }

        private String configuredValue() {
            return configuredValue;
        }

        private int setterInvocationCount() {
            return setterInvocationCount;
        }
    }
}
