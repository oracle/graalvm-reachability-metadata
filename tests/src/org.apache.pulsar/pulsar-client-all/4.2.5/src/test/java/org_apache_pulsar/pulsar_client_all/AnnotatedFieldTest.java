/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.BeanDescription;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.introspect.AnnotatedField;
import org.junit.jupiter.api.Test;

public class AnnotatedFieldTest {

    @Test
    void readsAndWritesAFieldDiscoveredByJacksonIntrospection() {
        final ObjectMapper mapper = new ObjectMapper();
        final BeanDescription description = mapper.getSerializationConfig()
                .introspect(mapper.constructType(FieldBean.class));
        final AnnotatedField field = description.findProperties().stream()
                .filter(candidate -> candidate.getName().equals("topic"))
                .findFirst()
                .orElseThrow()
                .getField();
        final FieldBean bean = new FieldBean();

        field.setValue(bean, "persistent://public/default/orders");

        assertThat(field.getValue(bean)).isEqualTo("persistent://public/default/orders");
    }

    public static final class FieldBean {
        public String topic;
    }
}
