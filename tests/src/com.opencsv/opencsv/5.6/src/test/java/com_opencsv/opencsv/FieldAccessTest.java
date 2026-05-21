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
    void readsWithOptionalGetterAndWritesWithTypedSetter() throws Exception {
        FieldAccess<String> fieldAccess = fieldAccessFor(OptionalGetterTypedSetterBean.class, "value");
        OptionalGetterTypedSetterBean bean = new OptionalGetterTypedSetterBean();

        fieldAccess.setField(bean, "written");

        assertThat(fieldAccess.getField(bean)).isEqualTo("written");
        assertThat(bean.setWithTypedSetter).isTrue();
    }

    @Test
    void writesWithOptionalSetterWhenTypedSetterIsUnavailable() throws Exception {
        FieldAccess<String> fieldAccess = fieldAccessFor(OptionalSetterBean.class, "value");
        OptionalSetterBean bean = new OptionalSetterBean();

        fieldAccess.setField(bean, "wrapped");

        assertThat(bean.assignedValue).contains("wrapped");
    }

    private static FieldAccess<String> fieldAccessFor(Class<?> beanType, String fieldName) throws NoSuchFieldException {
        Field field = beanType.getDeclaredField(fieldName);
        return new FieldAccess<>(field);
    }

    public static class OptionalGetterTypedSetterBean {
        public String value;
        public boolean setWithTypedSetter;

        public Optional<String> getValue() {
            return Optional.ofNullable(value);
        }

        public void setValue(String value) {
            this.value = value;
            setWithTypedSetter = true;
        }
    }

    public static class OptionalSetterBean {
        public String value;
        public Optional<String> assignedValue = Optional.empty();

        public String getValue() {
            return value;
        }

        public void setValue(Optional<String> value) {
            assignedValue = value;
            this.value = value.orElse(null);
        }
    }
}
