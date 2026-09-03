/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.property.access.internal.PropertyAccessStrategyBasicImpl;
import org.hibernate.property.access.spi.Getter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GetterMethodImplTest {

    @Test
    public void readsAPropertyThroughItsGetter() {
        Getter getter = PropertyAccessStrategyBasicImpl.INSTANCE
                .buildPropertyAccess(PropertyValue.class, "value", false)
                .getGetter();

        assertThat(getter.get(new PropertyValue())).isEqualTo("hibernate");
    }

    public static class PropertyValue {
        public String getValue() {
            return "hibernate";
        }
    }
}
