/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.sisu.bean.BeanProperties;
import org.eclipse.sisu.bean.BeanProperty;
import org.junit.jupiter.api.Test;

public class BeanPropertyFieldTest {
    @Test
    void beanPropertySetsPrivateFieldValue() {
        FieldBackedBean bean = new FieldBackedBean();

        BeanProperty<Object> property = findProperty(FieldBackedBean.class, "message");
        property.set(bean, "configured by field property");

        assertThat(bean.message()).isEqualTo("configured by field property");
    }

    private static BeanProperty<Object> findProperty(Class<?> beanType, String name) {
        for (BeanProperty<Object> property : new BeanProperties(beanType)) {
            if (name.equals(property.getName())) {
                return property;
            }
        }
        throw new AssertionError("Missing bean property: " + name);
    }

    private static final class FieldBackedBean {
        private String message = "unset";

        String message() {
            return message;
        }
    }
}
