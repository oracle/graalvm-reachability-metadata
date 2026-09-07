/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.introspector.BeanAccess;
import org.testcontainers.shaded.org.yaml.snakeyaml.introspector.Property;
import org.testcontainers.shaded.org.yaml.snakeyaml.introspector.PropertyUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class SnakeYamlPropertyUtilsTest {
    @Test
    void discoversPropertiesUsingBeanAndFieldAccess() {
        PropertyUtils properties = new PropertyUtils();
        PropertyUtils fieldProperties = new PropertyUtils();
        fieldProperties.setBeanAccess(BeanAccess.FIELD);

        assertThat(properties.getProperties(Bean.class)).extracting(Property::getName).contains("property");
        assertThat(fieldProperties.getProperties(Bean.class, BeanAccess.FIELD))
            .extracting(Property::getName)
            .contains("field");
    }

    public static class Bean {
        private String field;
        private String property;

        public String getProperty() {
            return property;
        }

        public void setProperty(String property) {
            this.property = property;
        }
    }
}
