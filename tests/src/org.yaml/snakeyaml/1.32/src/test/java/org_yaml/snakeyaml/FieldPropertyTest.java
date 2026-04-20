/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

public class FieldPropertyTest {

    @Test
    void writesInheritedPrivateFieldThroughFieldProperty() throws Exception {
        Property property = new PropertyUtils().getProperty(
                FieldBackedBean.class, "value", BeanAccess.FIELD);
        FieldBackedBean bean = new FieldBackedBean();

        property.set(bean, "updated");

        assertThat(bean.readValue()).isEqualTo("updated");
    }

    @Test
    void readsInheritedPrivateFieldThroughFieldProperty() {
        Property property = new PropertyUtils().getProperty(
                FieldBackedBean.class, "value", BeanAccess.FIELD);
        FieldBackedBean bean = new FieldBackedBean();
        bean.writeValue("loaded");

        assertThat(property.get(bean)).isEqualTo("loaded");
    }

    private static class FieldBackedBase {
        private String value;

        String readValue() {
            return value;
        }

        void writeValue(String value) {
            this.value = value;
        }
    }

    private static final class FieldBackedBean extends FieldBackedBase {
    }
}
