/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.introspect.AnnotatedField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedFieldTest {
    @Test
    void readsAndWritesAFieldDiscoveredByJacksonIntrospection() {
        ObjectMapper mapper = new ObjectMapper();
        AnnotatedField field = mapper.getSerializationConfig()
                .introspect(mapper.constructType(FieldBean.class))
                .findProperties()
                .get(0)
                .getField();
        FieldBean bean = new FieldBean();

        field.setValue(bean, 42);

        assertThat(field.getValue(bean)).isEqualTo(42);
    }

    @Test
    void restoresAFieldDiscoveredByJacksonIntrospection() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AnnotatedField field = mapper.getSerializationConfig()
                .introspect(mapper.constructType(FieldBean.class))
                .findProperties()
                .get(0)
                .getField();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(field);
        }

        AnnotatedField restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (AnnotatedField) input.readObject();
        }

        FieldBean bean = new FieldBean();
        restored.setValue(bean, 84);
        assertThat(restored.getValue(bean)).isEqualTo(84);
    }

    public static class FieldBean {
        public int value;
    }
}
