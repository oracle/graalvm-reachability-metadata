/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencsv.bean.FieldAccess;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class FieldAccessTest {
    @Test
    void usesPublicStringGetterAndSetterWhenAvailable() throws Exception {
        StringAccessorBean bean = new StringAccessorBean();
        FieldAccess<String> fieldAccess = newFieldAccess(StringAccessorBean.class, "value");

        fieldAccess.setField(bean, "alpha");

        assertThat(bean.value).isEqualTo("set:alpha");
        assertThat(bean.setterCallCount).isOne();
        assertThat(fieldAccess.getField(bean)).isEqualTo("get:set:alpha");
        assertThat(bean.getterCallCount).isOne();
    }

    @Test
    void unwrapsOptionalGetterAndWrapsOptionalSetterWhenAvailable() throws Exception {
        OptionalAccessorBean bean = new OptionalAccessorBean();
        FieldAccess<String> fieldAccess = newFieldAccess(OptionalAccessorBean.class, "alias");

        fieldAccess.setField(bean, "beta");

        assertThat(bean.alias).isEqualTo("set:beta");
        assertThat(bean.setterCallCount).isOne();
        assertThat(fieldAccess.getField(bean)).isEqualTo("get:set:beta");
        assertThat(bean.getterCallCount).isOne();
    }

    private static FieldAccess<String> newFieldAccess(Class<?> beanType, String fieldName) throws NoSuchFieldException {
        Field field = beanType.getDeclaredField(fieldName);
        return new FieldAccess<>(field);
    }

    public static class StringAccessorBean {
        private String value = "initial";
        private int getterCallCount;
        private int setterCallCount;

        public String getValue() {
            getterCallCount++;
            return "get:" + value;
        }

        public void setValue(String value) {
            setterCallCount++;
            this.value = "set:" + value;
        }
    }

    public static class OptionalAccessorBean {
        private String alias;
        private int getterCallCount;
        private int setterCallCount;

        public Optional<String> getAlias() {
            getterCallCount++;
            return Optional.ofNullable(alias).map(value -> "get:" + value);
        }

        public void setAlias(Optional<String> alias) {
            setterCallCount++;
            this.alias = alias.map(value -> "set:" + value).orElse(null);
        }
    }
}
