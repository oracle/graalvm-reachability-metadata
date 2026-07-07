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

import java.lang.reflect.Field;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BeanFieldSingleValueTest {

    @Test
    void setFieldValueReportsMissingConverterWithLocalizedMessage() throws Exception {
        BeanFieldSingleValue<SimpleBean, String> field = singleValueField();
        SimpleBean bean = new SimpleBean();

        assertThatThrownBy(() -> field.setFieldValue(bean, "alpha", "value"))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining("converter is necessary");
    }

    @Test
    void writeReportsMissingConverterWithLocalizedMessage() throws Exception {
        BeanFieldSingleValue<SimpleBean, String> field = singleValueField();
        SimpleBean bean = new SimpleBean();
        bean.value = "alpha";

        assertThatThrownBy(() -> field.write(bean, "value"))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining("converter is necessary");
    }

    private static BeanFieldSingleValue<SimpleBean, String> singleValueField()
            throws NoSuchFieldException {
        Field field = SimpleBean.class.getField("value");
        return new BeanFieldSingleValue<>(SimpleBean.class, field, false, Locale.US, null, "", "");
    }

    public static class SimpleBean {
        public String value;
    }
}
