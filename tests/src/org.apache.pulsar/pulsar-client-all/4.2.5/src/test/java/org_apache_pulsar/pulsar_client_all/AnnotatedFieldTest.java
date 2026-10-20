/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.introspect.AnnotatedField;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.junit.jupiter.api.Test;

public class AnnotatedFieldTest {
    @Test
    void serializedAnnotatedFieldCanReadAndWriteBeanValue() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BeanPropertyDefinition property = mapper.getSerializationConfig()
                .introspect(mapper.constructType(FieldBean.class))
                .findProperties()
                .stream()
                .filter(candidate -> candidate.getName().equals("value"))
                .findFirst()
                .orElseThrow();
        AnnotatedField restored = SerializationSupport.roundTrip(property.getField());
        FieldBean bean = new FieldBean();

        restored.setValue(bean, "updated");

        assertThat(restored.getValue(bean)).isEqualTo("updated");
        assertThat(bean.value).isEqualTo("updated");
    }

    public static class FieldBean {
        public String value;
    }
}
