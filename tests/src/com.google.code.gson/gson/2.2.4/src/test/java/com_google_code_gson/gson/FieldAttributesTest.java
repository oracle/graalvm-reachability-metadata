/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.FieldAttributes;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldAttributesTest {
    @Test
    public void exposesMetadataForTheWrappedField() throws Exception {
        final Field field = SampleModel.class.getDeclaredField("description");
        final FieldAttributes attributes = new FieldAttributes(field);

        assertThat(attributes.getDeclaringClass()).isEqualTo(SampleModel.class);
        assertThat(attributes.getName()).isEqualTo("description");
        assertThat(attributes.getDeclaredType()).isEqualTo(String.class);
        assertThat(attributes.getDeclaredClass()).isEqualTo(String.class);
        // Checkstyle: allow direct annotation access
        assertThat(((java.util.function.Function<Class<Deprecated>, Deprecated>) attributes::getAnnotation).apply(Deprecated.class)).isNotNull();
        assertThat(((java.util.function.Supplier<java.util.Collection<Annotation>>) attributes::getAnnotations).get())
                .extracting(Annotation::annotationType)
                .contains(Deprecated.class);
        // Checkstyle: disallow direct annotation access
        assertThat(attributes.hasModifier(Modifier.PUBLIC)).isTrue();
        assertThat(attributes.hasModifier(Modifier.STATIC)).isFalse();
    }

    @Test
    public void readsValueFromTheWrappedField() throws Exception {
        final Field field = SampleModel.class.getDeclaredField("description");
        final FieldAttributes attributes = new FieldAttributes(field);
        final SampleModel sample = new SampleModel("stored field value");

        assertThat(readValue(attributes, sample)).isEqualTo("stored field value");
    }

    private static Object readValue(final FieldAttributes attributes, final Object instance) throws Exception {
        final Method getMethod = FieldAttributes.class.getDeclaredMethod("get", Object.class);
        getMethod.setAccessible(true);
        return getMethod.invoke(attributes, instance);
    }

    public static final class SampleModel {
        @Deprecated
        public String description;

        public SampleModel(final String description) {
            this.description = description;
        }
    }
}
