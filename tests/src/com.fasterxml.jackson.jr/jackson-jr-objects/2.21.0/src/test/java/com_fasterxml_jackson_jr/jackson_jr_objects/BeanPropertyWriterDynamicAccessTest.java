/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.fasterxml.jackson.jr.ob.impl.BeanPropertyWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyWriterDynamicAccessTest {
    @Test
    void getsFieldBackedPropertyValues() throws Exception {
        BeanPropertyWriter writer = new BeanPropertyWriter(-1, "id",
                publicField(FieldBackedWriterBean.class, "id"), null);

        Object value = writer.getValueFor(new FieldBackedWriterBean());

        assertThat(value).isEqualTo(3);
    }

    @Test
    void invokesGetterBackedPropertyValues() throws Exception {
        BeanPropertyWriter writer = new BeanPropertyWriter(-1, "name", null,
                publicMethod(GetterBackedWriterBean.class, "getName"));

        Object value = writer.getValueFor(new GetterBackedWriterBean("Ada"));

        assertThat(value).isEqualTo("Ada");
    }

    private static Field publicField(Class<?> declaringType, String fieldName) {
        try {
            return declaringType.getField(fieldName);
        } catch (NoSuchFieldException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Method publicMethod(Class<?> declaringType, String methodName,
            Class<?>... parameterTypes) {
        try {
            return declaringType.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static final class FieldBackedWriterBean {
        public int id = 3;
    }

    public static final class GetterBackedWriterBean {
        private final String name;

        public GetterBackedWriterBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
