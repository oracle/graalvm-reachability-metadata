/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.activemq.util.IntrospectionSupport;
import org.junit.jupiter.api.Test;

public class IntrospectionSupportTest {
    @Test
    void getPropertiesReadsPublicBeanGetters() {
        PropertyTarget target = new PropertyTarget();
        target.setName("broker");
        target.setPort(61616);
        target.setEnabled(Boolean.TRUE);

        Map<String, String> properties = new LinkedHashMap<>();
        boolean found = IntrospectionSupport.getProperties(target, properties, "wire.");

        assertThat(found).isTrue();
        assertThat(properties)
                .containsEntry("wire.name", "broker")
                .containsEntry("wire.port", "61616")
                .containsEntry("wire.enabled", "true");
    }

    @Test
    void setPropertyInvokesSetterWithExactAndConvertedValues() {
        PropertyTarget target = new PropertyTarget();

        assertThat(IntrospectionSupport.setProperty(target, "name", "client")).isTrue();
        assertThat(IntrospectionSupport.setProperty(target, "port", "61617")).isTrue();

        assertThat(target.getName()).isEqualTo("client");
        assertThat(target.getPort()).isEqualTo(61617);
    }

    @Test
    void finderMethodsScanPublicBeanMethods() {
        Method setter = IntrospectionSupport.findSetterMethod(PropertyTarget.class, "name");
        Method getter = IntrospectionSupport.findGetterMethod(PropertyTarget.class, "name");

        assertThat(setter).isNotNull();
        assertThat(setter.getName()).isEqualTo("setName");
        assertThat(getter).isNotNull();
        assertThat(getter.getName()).isEqualTo("getName");
    }

    @Test
    void toStringReadsNonPrivateFieldsAcrossHierarchy() {
        FieldTarget target = new FieldTarget();

        String description = IntrospectionSupport.toString(target);

        assertThat(description)
                .contains("FieldTarget {")
                .contains("inherited = base")
                .contains("name = field-client")
                .contains("password = *****")
                .contains("options = [fast, durable]")
                .doesNotContain("secret")
                .doesNotContain("privateValue")
                .doesNotContain("transientValue")
                .doesNotContain("staticValue");
    }

    public static class PropertyTarget {
        private String name;
        private int port;
        private Boolean enabled;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public Boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getNullValue() {
            return null;
        }
    }

    public static class FieldTargetBase {
        static String staticValue = "static";
        transient String transientValue = "transient";
        private String privateValue = "private";
        String inherited = "base";
    }

    public static class FieldTarget extends FieldTargetBase {
        String name = "field-client";
        String password = "secret";
        String[] options = {"fast", "durable"};
    }
}
