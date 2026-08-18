/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.sisu.bean.BeanProperties;
import org.eclipse.sisu.bean.BeanProperty;
import org.eclipse.sisu.bean.IgnoreSetters;
import org.junit.jupiter.api.Test;

public class BeanPropertyFieldTest {
    @Test
    void setUpdatesPrivateFieldDiscoveredAsBeanProperty() {
        FieldBackedBean bean = new FieldBackedBean();
        BeanProperty<Object> property = firstPropertyOf(FieldBackedBean.class);

        property.set(bean, "configured");

        assertThat(property.getName()).isEqualTo("value");
        assertThat(bean.value()).isEqualTo("configured");
    }

    private static BeanProperty<Object> firstPropertyOf(Class<?> beanType) {
        return new BeanProperties(beanType).iterator().next();
    }

    @IgnoreSetters
    private static final class FieldBackedBean {
        private String value;

        private String value() {
            return value;
        }
    }
}
