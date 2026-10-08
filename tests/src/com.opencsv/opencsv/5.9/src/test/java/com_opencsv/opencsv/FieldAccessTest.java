/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.FieldAccess;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldAccessTest {

    @Test
    void setFieldUsesBeanSetterWithFieldType() throws Exception {
        FieldAccess<String> access = fieldAccess(SetterBean.class, "value");
        SetterBean bean = new SetterBean();

        access.setField(bean, "assigned");

        assertThat(bean.value).isEqualTo("setter:assigned");
    }

    @Test
    void getAndSetFieldUseOptionalAccessors() throws Exception {
        FieldAccess<String> access = fieldAccess(OptionalAccessorBean.class, "value");
        OptionalAccessorBean bean = new OptionalAccessorBean();
        bean.value = "readable";

        assertThat(access.getField(bean)).isEqualTo("readable");

        access.setField(bean, "assigned");

        assertThat(bean.value).isEqualTo("optional:assigned");
    }

    private static FieldAccess<String> fieldAccess(Class<?> beanType, String fieldName) throws NoSuchFieldException {
        Field field = beanType.getField(fieldName);
        return new FieldAccess<>(field);
    }

    public static class SetterBean {
        public String value;

        public void setValue(String value) {
            this.value = "setter:" + value;
        }
    }

    public static class OptionalAccessorBean {
        public String value;

        public Optional<String> getValue() {
            return Optional.ofNullable(value);
        }

        public void setValue(Optional<String> value) {
            this.value = "optional:" + value.orElse("empty");
        }
    }
}
