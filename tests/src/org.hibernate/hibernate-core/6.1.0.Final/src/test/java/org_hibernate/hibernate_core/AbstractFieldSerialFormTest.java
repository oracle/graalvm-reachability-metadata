/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.SerializationHelper;
import org.hibernate.property.access.internal.PropertyAccessStrategyFieldImpl;
import org.hibernate.property.access.spi.Getter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractFieldSerialFormTest {

    @Test
    public void restoresFieldAccessAfterSerialization() {
        Getter getter = PropertyAccessStrategyFieldImpl.INSTANCE
                .buildPropertyAccess(PropertyValue.class, "value", false)
                .getGetter();

        Getter copy = (Getter) SerializationHelper.clone(getter);

        assertThat(copy.get(new PropertyValue())).isEqualTo("field-value");
    }

    public static class PropertyValue {
        private String value = "field-value";
    }
}
