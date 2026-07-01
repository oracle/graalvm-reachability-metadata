/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.activemq.util.IntrospectionSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionSupportTest {

    @Test
    void getPropertiesReadsConvertibleJavaBeanValues() {
        PropertyBean bean = new PropertyBean();
        bean.setName("orders");
        bean.setMessageCount(42);
        bean.setDurable(true);

        Map<String, Object> properties = new LinkedHashMap<>();

        boolean foundProperties = IntrospectionSupport.getProperties(bean, properties, "broker.");

        assertThat(foundProperties).isTrue();
        assertThat(properties)
                .containsEntry("broker.name", "orders")
                .containsEntry("broker.messageCount", "42")
                .containsEntry("broker.durable", "true");
    }

    @Test
    void setPropertyInvokesSettersWithDirectAndConvertedValues() {
        PropertyBean bean = new PropertyBean();

        boolean nameSet = IntrospectionSupport.setProperty(bean, "name", "inventory");
        boolean countSet = IntrospectionSupport.setProperty(bean, "messageCount", "17");
        boolean durableSet = IntrospectionSupport.setProperty(bean, "durable", "true");

        assertThat(nameSet).isTrue();
        assertThat(countSet).isTrue();
        assertThat(durableSet).isTrue();
        assertThat(bean.getName()).isEqualTo("inventory");
        assertThat(bean.getMessageCount()).isEqualTo(17);
        assertThat(bean.isDurable()).isTrue();
    }

    @Test
    void findAccessorMethodsUsesBeanNamingConventions() {
        Method setter = IntrospectionSupport.findSetterMethod(PropertyBean.class, "messageCount");
        Method getter = IntrospectionSupport.findGetterMethod(PropertyBean.class, "messageCount");

        assertThat(setter).isNotNull();
        assertThat(setter.getName()).isEqualTo("setMessageCount");
        assertThat(getter).isNotNull();
        assertThat(getter.getName()).isEqualTo("getMessageCount");
    }

    @Test
    void toStringIncludesSupportedFieldsAndMasksPasswordValues() {
        FieldCarrier carrier = new FieldCarrier();
        carrier.id = "queue-1";
        carrier.messageCount = 5;
        carrier.password = "secret";
        carrier.values = new String[] {"first", "second"};

        String description = IntrospectionSupport.toString(carrier);

        assertThat(description)
                .contains("FieldCarrier {")
                .contains("id = queue-1")
                .contains("messageCount = 5")
                .contains("password = *****")
                .contains("values = [first, second]");
    }

    public static final class PropertyBean {
        private String name;
        private int messageCount;
        private boolean durable;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getMessageCount() {
            return messageCount;
        }

        public void setMessageCount(int messageCount) {
            this.messageCount = messageCount;
        }

        public boolean isDurable() {
            return durable;
        }

        public void setDurable(boolean durable) {
            this.durable = durable;
        }
    }

    public static final class FieldCarrier {
        public String id;
        public int messageCount;
        public String password;
        public String[] values;
        private String internal = "ignored";
    }
}
