/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.BeanFieldSingleValue;
import com.opencsv.exceptions.CsvBadConverterException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BeanFieldSingleValueTest {
    @Test
    void reportsMissingConverterWhenReadingSingleValue() throws Exception {
        BeanFieldSingleValue<SingleValueBean, String> beanField = beanFieldWithoutConverter();

        assertThatThrownBy(() -> beanField.setFieldValue(new SingleValueBean(), "value", "value"))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining("converter");
    }

    @Test
    void reportsMissingConverterWhenWritingSingleValue() throws Exception {
        BeanFieldSingleValue<SingleValueBean, String> beanField = beanFieldWithoutConverter();
        SingleValueBean bean = new SingleValueBean();
        bean.value = "value";

        assertThatThrownBy(() -> beanField.write(bean, "value"))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining("converter");
    }

    private static BeanFieldSingleValue<SingleValueBean, String> beanFieldWithoutConverter()
            throws NoSuchFieldException {
        return new BeanFieldSingleValue<>(
                SingleValueBean.class,
                SingleValueBean.class.getDeclaredField("value"),
                false,
                Locale.US,
                null,
                "",
                "");
    }

    public static class SingleValueBean {
        public String value;
    }
}
