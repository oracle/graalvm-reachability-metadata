/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.MappedField;
import org.mongodb.morphia.mapping.Mapper;

public class MappedFieldTest {
    @Test
    void readsAndWritesMappedPrivateFieldValues() {
        MappedField mappedField = mappedField(FieldAccessHolder.class, "name");
        FieldAccessHolder holder = new FieldAccessHolder("initial");

        assertThat(mappedField.getFieldValue(holder)).isEqualTo("initial");

        mappedField.setFieldValue(holder, "updated");

        assertThat(holder.name).isEqualTo("updated");
    }

    @Test
    void discoversConcreteConstructorFromFieldAnnotation() {
        MappedField mappedField = mappedField(ConcreteAnnotationHolder.class, "value");

        assertThat(mappedField.getConcreteType()).isEqualTo(ConcreteEmbeddedValue.class);
        assertThat(mappedField.getCTor()).isNotNull();
        assertThat(mappedField.getCTor().getDeclaringClass()).isEqualTo(ConcreteEmbeddedValue.class);
    }

    @Test
    void resolvesGenericArrayFieldsToConcreteArrayTypes() {
        MappedField mappedField = mappedField(StringArrayHolder.class, "values");

        assertThat(mappedField.isArray()).isTrue();
        assertThat(mappedField.getType()).isEqualTo(String[].class);
        assertThat(mappedField.getSubClass()).isEqualTo(String.class);
    }

    private static MappedField mappedField(Class<?> holderType, String fieldName) {
        Mapper mapper = new Mapper();
        MappedClass mappedClass = mapper.addMappedClass(holderType);
        MappedField mappedField = mappedClass.getMappedFieldByJavaField(fieldName);
        assertThat(mappedField).isNotNull();
        return mappedField;
    }

    @Embedded
    public static class FieldAccessHolder {
        private String name;

        FieldAccessHolder(String name) {
            this.name = name;
        }
    }

    @Embedded
    public static class ConcreteAnnotationHolder {
        @Embedded(concreteClass = ConcreteEmbeddedValue.class)
        private EmbeddedValue value;
    }

    public interface EmbeddedValue {
    }

    public static class ConcreteEmbeddedValue implements EmbeddedValue {
        private ConcreteEmbeddedValue() {
        }
    }

    @Embedded
    public static class GenericArrayHolder<T> {
        private T[] values;
    }

    public static class StringArrayHolder extends GenericArrayHolder<String> {
    }
}
