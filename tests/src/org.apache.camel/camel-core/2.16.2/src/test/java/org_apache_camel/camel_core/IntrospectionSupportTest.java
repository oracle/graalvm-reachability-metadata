/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.util.IntrospectionSupport;
import org.junit.jupiter.api.Test;

public class IntrospectionSupportTest {
    @Test
    void readsBeanPropertiesUsingBooleanAndRegularGetters() throws Exception {
        ConfiguredBean bean = new ConfiguredBean();
        bean.setEnabled(true);
        bean.setName("camel");

        assertThat(IntrospectionSupport.getProperty(bean, "enabled")).isEqualTo(Boolean.TRUE);
        assertThat(IntrospectionSupport.getProperty(bean, "name")).isEqualTo("camel");
        assertThat(IntrospectionSupport.getPropertyGetter(ConfiguredBean.class, "enabled").getName())
                .isEqualTo("isEnabled");
        assertThat(IntrospectionSupport.getPropertyGetter(ConfiguredBean.class, "name").getName())
                .isEqualTo("getName");
    }

    @Test
    void extractsReadableAndWritablePropertiesFromBean() {
        ConfiguredBean bean = new ConfiguredBean();
        bean.setEnabled(true);
        bean.setName("camel");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        boolean foundProperties = IntrospectionSupport.getProperties(bean, properties, "bean.", false);

        assertThat(foundProperties).isTrue();
        assertThat(properties)
                .containsEntry("bean.enabled", Boolean.TRUE)
                .containsEntry("bean.name", "camel");
    }

    @Test
    void findsPropertySetterAndSetsAssignableValue() throws Exception {
        ConfiguredBean bean = new ConfiguredBean();

        assertThat(IntrospectionSupport.getPropertySetter(ConfiguredBean.class, "name").getName())
                .isEqualTo("setName");
        assertThat(IntrospectionSupport.setProperty(bean, "name", "Apache Camel")).isTrue();

        assertThat(bean.getName()).isEqualTo("Apache Camel");
    }

    @Test
    void convertsStringValueBeforeSettingUriProperty() throws Exception {
        ConfiguredBean bean = new ConfiguredBean();

        assertThat(IntrospectionSupport.setProperty(null, bean, "endpoint", "direct:start")).isTrue();

        assertThat(bean.getEndpoint()).isEqualTo(new URI("direct:start"));
    }

    public static class ConfiguredBean {
        private boolean enabled;
        private String name;
        private URI endpoint;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public URI getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(URI endpoint) {
            this.endpoint = endpoint;
        }
    }
}
