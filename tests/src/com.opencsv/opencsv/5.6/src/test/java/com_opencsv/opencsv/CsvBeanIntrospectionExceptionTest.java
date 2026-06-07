/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencsv.exceptions.CsvBeanIntrospectionException;
import java.lang.reflect.Field;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class CsvBeanIntrospectionExceptionTest {
    @Test
    void formatsDefaultMessageFromBundleForBeanAndField() throws Exception {
        Field field = IntrospectedBean.class.getDeclaredField("value");
        IntrospectedBean bean = new IntrospectedBean();
        CsvBeanIntrospectionException exception = new CsvBeanIntrospectionException(bean, field);

        assertThat(exception.getMessage())
                .contains("value")
                .contains(IntrospectedBean.class.getCanonicalName());
        assertThat(exception.getLocalizedMessage())
                .contains("value")
                .contains(IntrospectedBean.class.getCanonicalName());
    }

    @Test
    void preservesExplicitMessageWithoutLoadingDefaultBundle() throws Exception {
        Field field = IntrospectedBean.class.getDeclaredField("value");
        CsvBeanIntrospectionException exception = new CsvBeanIntrospectionException(
                new IntrospectedBean(), field, "custom");

        assertThat(exception.getMessage()).isEqualTo("custom");
        assertThat(exception.getLocalizedMessage()).isEqualTo("custom");
    }

    @Test
    void formatsLocalizedDefaultMessageFromBundle() throws Exception {
        Field field = IntrospectedBean.class.getDeclaredField("value");
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            CsvBeanIntrospectionException exception = new CsvBeanIntrospectionException(new IntrospectedBean(), field);

            assertThat(exception.getLocalizedMessage())
                    .contains("value")
                    .contains(IntrospectedBean.class.getCanonicalName());
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    public static class IntrospectedBean {
        private String value;
    }
}
