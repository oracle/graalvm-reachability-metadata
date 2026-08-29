/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.SerializationHelper;
import org.hibernate.property.access.internal.PropertyAccessStrategyBasicImpl;
import org.hibernate.property.access.spi.Setter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SetterMethodImplInnerSerialFormTest {

    @Test
    public void restoresSetterMethodAccessAfterSerialization() {
        Setter setter = PropertyAccessStrategyBasicImpl.INSTANCE
                .buildPropertyAccess(PropertyValue.class, "value", true)
                .getSetter();
        Setter copy = (Setter) SerializationHelper.clone(setter);
        PropertyValue target = new PropertyValue();

        copy.set(target, "method-value");

        assertThat(target.getValue()).isEqualTo("method-value");
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
