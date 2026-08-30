/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.property.access.internal.PropertyAccessStrategyBasicImpl;
import org.hibernate.property.access.spi.Setter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SetterMethodImplTest {

    @Test
    public void writesAPropertyThroughItsSetter() {
        PropertyValue target = new PropertyValue();
        Setter setter = PropertyAccessStrategyBasicImpl.INSTANCE
                .buildPropertyAccess(PropertyValue.class, "value", true)
                .getSetter();

        setter.set(target, "hibernate");

        assertThat(target.getValue()).isEqualTo("hibernate");
    }

    public static class PropertyValue {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
