/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.exceptions.CsvBeanIntrospectionException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

public class CsvBeanIntrospectionExceptionTest {
    @Test
    void loadsDefaultErrorMessageFromResourceBundleWhenBeanAndFieldAreAvailable() throws NoSuchFieldException {
        ExampleBean bean = new ExampleBean();
        Field field = ExampleBean.class.getField("value");

        CsvBeanIntrospectionException exception = new CsvBeanIntrospectionException(bean, field);

        assertThat(exception.getMessage())
                .contains("An introspection error was thrown")
                .contains("value")
                .contains(ExampleBean.class.getCanonicalName());
    }

    public static class ExampleBean {
        public String value;
    }
}
