/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.mapping.MappedField;
import org.mongodb.morphia.mapping.Mapper;

import static org.assertj.core.api.Assertions.assertThat;

public class MappedFieldTest {
    @Test
    public void readsAndWritesPrivateFieldValues() {
        final Mapper mapper = new Mapper();
        final MappedField mappedField = mapper.getMappedClass(FieldAccessHolder.class)
                .getMappedFieldByJavaField("name");
        final FieldAccessHolder holder = new FieldAccessHolder();

        mappedField.setFieldValue(holder, "morphia");

        assertThat(mappedField.getFieldValue(holder)).isEqualTo("morphia");
        assertThat(holder.name).isEqualTo("morphia");
    }

    @Test
    public void discoversConcreteClassConstructorFromEmbeddedAnnotation() {
        final Mapper mapper = new Mapper();
        final MappedField mappedField = mapper.getMappedClass(ConcreteEmbeddedHolder.class)
                .getMappedFieldByJavaField("value");

        assertThat(mappedField.getConcreteType()).isEqualTo(ConcreteEmbeddedValue.class);
        assertThat(mappedField.getCTor()).isNotNull();
    }

    @Test
    public void discoversDeclaredConstructorFromFieldType() {
        final Mapper mapper = new Mapper();
        final MappedField mappedField = mapper.getMappedClass(DeclaredConstructorHolder.class)
                .getMappedFieldByJavaField("value");

        assertThat(mappedField.getType()).isEqualTo(DeclaredConstructorValue.class);
        assertThat(mappedField.getCTor()).isNotNull();
    }

    @Test
    public void resolvesGenericArrayFieldsUsingMappedSubType() {
        final Mapper mapper = new Mapper();
        final MappedField mappedField = mapper.getMappedClass(StringArrayHolder.class)
                .getMappedFieldByJavaField("values");

        assertThat(mappedField.isArray()).isTrue();
        assertThat(mappedField.getType()).isEqualTo(String[].class);
        assertThat(mappedField.getSubClass()).isEqualTo(String.class);
    }

    public static final class FieldAccessHolder {
        private String name;
    }

    public interface EmbeddedValue {
    }

    public static final class ConcreteEmbeddedHolder {
        @Embedded(concreteClass = ConcreteEmbeddedValue.class)
        private EmbeddedValue value;
    }

    public static final class ConcreteEmbeddedValue implements EmbeddedValue {
        private ConcreteEmbeddedValue() {
        }
    }

    public static final class DeclaredConstructorHolder {
        @Embedded
        private DeclaredConstructorValue value;
    }

    public static final class DeclaredConstructorValue {
        private DeclaredConstructorValue() {
        }
    }

    public static class GenericArrayHolder<T> {
        private T[] values;
    }

    public static final class StringArrayHolder extends GenericArrayHolder<String> {
    }
}
