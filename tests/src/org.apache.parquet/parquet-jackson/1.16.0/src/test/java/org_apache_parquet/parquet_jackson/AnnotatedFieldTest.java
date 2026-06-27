/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.BeanDescription;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.introspect.AnnotatedField;
import shaded.parquet.com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

public class AnnotatedFieldTest {
    @Test
    void annotatedFieldReadsAndWritesBeanFieldValues() {
        final AnnotatedField field = annotatedField("value");
        final FieldBackedBean bean = new FieldBackedBean("initial");

        assertThat(field.getValue(bean)).isEqualTo("initial");

        field.setValue(bean, "updated");

        assertThat(bean.value).isEqualTo("updated");
        assertThat(field.getValue(bean)).isEqualTo("updated");
    }

    @Test
    void serializedAnnotatedFieldResolvesDeclaredBeanField() throws Exception {
        final AnnotatedField field = annotatedField("value");

        final AnnotatedField resolved = roundTrip(field);

        assertThat(resolved.getName()).isEqualTo("value");
        assertThat(resolved.getDeclaringClass()).isEqualTo(FieldBackedBean.class);
        assertThat(resolved.getValue(new FieldBackedBean("restored"))).isEqualTo("restored");
    }

    private static AnnotatedField annotatedField(String name) {
        final ObjectMapper mapper = new ObjectMapper();
        final BeanDescription description = mapper.getSerializationConfig()
                .introspect(mapper.constructType(FieldBackedBean.class));
        final List<BeanPropertyDefinition> properties = description.findProperties();

        return properties.stream()
                .filter(property -> name.equals(property.getName()))
                .map(BeanPropertyDefinition::getField)
                .filter(field -> field != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing annotated field: " + name));
    }

    private static AnnotatedField roundTrip(AnnotatedField field) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(field);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (AnnotatedField) input.readObject();
        }
    }

    public static final class FieldBackedBean {
        public String value;

        public FieldBackedBean(String value) {
            this.value = value;
        }
    }
}
