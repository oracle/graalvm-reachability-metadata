/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.MappedField;
import org.mongodb.morphia.mapping.Mapper;

import static org.assertj.core.api.Assertions.assertThat;

public class MappedFieldTest {
    @Test
    void readsAndWritesPrivateFieldValues() {
        Mapper mapper = new Mapper();
        MappedField mappedField = mappedField(mapper, MappedFieldPrivateValueHolder.class, "value");
        MappedFieldPrivateValueHolder holder = new MappedFieldPrivateValueHolder();

        mappedField.setFieldValue(holder, "updated");

        assertThat(mappedField.getFieldValue(holder)).isEqualTo("updated");
    }

    @Test
    void discoversConstructorFromConcreteClassAnnotation() {
        Mapper mapper = new Mapper();
        MappedField mappedField = mappedField(mapper, MappedFieldConcreteValueHolder.class, "value");

        assertThat(mappedField.getCTor()).isNotNull();
        assertThat(mappedField.getCTor().getDeclaringClass()).isEqualTo(MappedFieldConcreteValue.class);
    }

    @Test
    void discoversConstructorFromDeclaredFieldType() {
        Mapper mapper = new Mapper();
        MappedField mappedField = mappedField(mapper, MappedFieldDeclaredValueHolder.class, "value");

        assertThat(mappedField.getCTor()).isNotNull();
        assertThat(mappedField.getCTor().getDeclaringClass()).isEqualTo(MappedFieldDeclaredValue.class);
    }

    @Test
    void resolvesGenericArrayTypeToConcreteArrayClass() {
        Mapper mapper = new Mapper();
        MappedField mappedField = mappedField(mapper, MappedFieldStringArrayHolder.class, "values");

        assertThat(mappedField.isArray()).isTrue();
        assertThat(mappedField.getType()).isEqualTo(String[].class);
        assertThat(mappedField.getSubClass()).isEqualTo(String.class);
    }

    private static MappedField mappedField(final Mapper mapper, final Class<?> type, final String fieldName) {
        MappedClass mappedClass = mapper.getMappedClass(type);
        return mappedClass.getMappedFieldByJavaField(fieldName);
    }
}

class MappedFieldPrivateValueHolder {
    private String value = "initial";
}

class MappedFieldConcreteValueHolder {
    @Embedded(concreteClass = MappedFieldConcreteValue.class)
    private MappedFieldAbstractValue value;
}

abstract class MappedFieldAbstractValue {
}

class MappedFieldConcreteValue extends MappedFieldAbstractValue {
    private MappedFieldConcreteValue() {
    }
}

class MappedFieldDeclaredValueHolder {
    @Embedded
    private MappedFieldDeclaredValue value;
}

class MappedFieldDeclaredValue {
    private MappedFieldDeclaredValue() {
    }
}

class MappedFieldGenericArrayHolder<T> {
    private T[] values;
}

class MappedFieldStringArrayHolder extends MappedFieldGenericArrayHolder<String> {
}
