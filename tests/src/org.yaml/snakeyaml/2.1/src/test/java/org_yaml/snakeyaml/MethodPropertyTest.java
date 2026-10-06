/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.introspector.MethodProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

public class MethodPropertyTest {

    @Test
    void readsBeanValueThroughMethodPropertyGetter() {
        Property property = new PropertyUtils().getProperty(AccessorBackedBean.class, "value");
        AccessorBackedBean bean = new AccessorBackedBean();
        bean.setValue("loaded");

        assertThat(property).isInstanceOf(MethodProperty.class);
        assertThat(property.get(bean)).isEqualTo("loaded");
    }

    @Test
    void writesBeanValueThroughMethodPropertySetter() throws Exception {
        Property property = new PropertyUtils().getProperty(AccessorBackedBean.class, "value");
        AccessorBackedBean bean = new AccessorBackedBean();

        property.set(bean, "updated");

        assertThat(bean.getValue()).isEqualTo("updated");
    }

    public static final class AccessorBackedBean {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
