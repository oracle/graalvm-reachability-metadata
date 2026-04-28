/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.fasterxml.jackson.jr.ob.impl.BeanPropertyReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyReaderDynamicAccessTest {
    @Test
    void setsFieldBackedProperties() throws Exception {
        BeanPropertyReader reader = new BeanPropertyReader("name",
                publicField(FieldBackedReaderBean.class, "name"), null, -1);
        FieldBackedReaderBean bean = new FieldBackedReaderBean();

        reader.setValueFor(bean, new Object[] {"Ada"});

        assertThat(bean.name).isEqualTo("Ada");
    }

    @Test
    void invokesSetterBackedProperties() throws Exception {
        BeanPropertyReader reader = new BeanPropertyReader("name", null,
                publicMethod(PublicSetterBackedReaderBean.class, "setName", String.class), -1);
        PublicSetterBackedReaderBean bean = new PublicSetterBackedReaderBean();

        reader.setValueFor(bean, new Object[] {"Ada"});

        assertThat(bean.getName()).isEqualTo("Ada");
        assertThat(bean.getSetterCalls()).isEqualTo(1);
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

    public static final class FieldBackedReaderBean {
        public String name;
    }

    public static final class PublicSetterBackedReaderBean {
        private String name;
        private int setterCalls;

        public String getName() {
            return name;
        }

        public int getSetterCalls() {
            return setterCalls;
        }

        public void setName(String name) {
            this.name = name;
            setterCalls++;
        }
    }
}
