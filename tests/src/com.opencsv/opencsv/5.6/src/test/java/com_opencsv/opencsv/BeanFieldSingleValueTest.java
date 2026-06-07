/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.BeanFieldSingleValue;
import com.opencsv.exceptions.CsvBadConverterException;
import java.lang.reflect.Field;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class BeanFieldSingleValueTest {
    @Test
    void reportsMissingConverterWhenReadingSingleValue() throws Exception {
        BeanFieldSingleValue<Object, String> beanField = newSingleValueField();

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> beanField.setFieldValue(new SingleValueBean(), "input", "value"))
                .withMessageContaining("converter is necessary");
    }

    @Test
    void reportsMissingConverterWhenWritingSingleValue() throws Exception {
        BeanFieldSingleValue<Object, String> beanField = newSingleValueField();
        SingleValueBean bean = new SingleValueBean();
        bean.value = "output";

        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> beanField.write(bean, null))
                .withMessageContaining("converter is necessary");
    }

    private static BeanFieldSingleValue<Object, String> newSingleValueField() throws NoSuchFieldException {
        Field field = SingleValueBean.class.getDeclaredField("value");
        return new BeanFieldSingleValue<>(SingleValueBean.class, field, false, Locale.US, null, null, null);
    }

    public static class SingleValueBean {
        private String value;
    }
}
