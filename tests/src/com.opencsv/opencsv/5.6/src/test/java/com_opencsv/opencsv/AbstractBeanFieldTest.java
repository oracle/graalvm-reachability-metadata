/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.processor.ConvertEmptyOrBlankStringsToDefault;
import com.opencsv.bean.processor.PreAssignmentProcessor;
import com.opencsv.bean.processor.StringProcessor;
import com.opencsv.bean.validators.MustMatchRegexExpression;
import com.opencsv.bean.validators.PreAssignmentValidator;
import com.opencsv.bean.validators.StringValidator;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import com.opencsv.exceptions.CsvValidationException;
import java.lang.reflect.Field;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class AbstractBeanFieldTest {
    @Test
    void appliesPreAssignmentProcessorAndValidatorBeforeAssigningValue() throws Exception {
        SimpleBean bean = new SimpleBean();
        IdentityBeanField beanField = newBeanField(SimpleBean.class.getDeclaredField("processedValue"), false);

        beanField.setFieldValue(bean, " ", "processedValue");

        assertThat(bean.processedValue).isEqualTo("fallback");
    }

    @Test
    void reportsRequiredFieldEmptyWhenReadingBlankInput() throws Exception {
        SimpleBean bean = new SimpleBean();
        IdentityBeanField beanField = newBeanField(SimpleBean.class.getDeclaredField("requiredValue"), true);

        assertThatExceptionOfType(CsvRequiredFieldEmptyException.class)
                .isThrownBy(() -> beanField.setFieldValue(bean, " ", "requiredValue"));
    }

    @Test
    void reportsProcessorInstantiationFailure() throws Exception {
        SimpleBean bean = new SimpleBean();
        IdentityBeanField beanField = newBeanField(SimpleBean.class.getDeclaredField("badProcessorValue"), false);

        assertThatExceptionOfType(CsvValidationException.class)
                .isThrownBy(() -> beanField.setFieldValue(bean, "value", "badProcessorValue"))
                .withMessageContaining(StringProcessor.class.getName())
                .withMessageContaining("badProcessorValue");
    }

    @Test
    void reportsValidatorInstantiationFailure() throws Exception {
        SimpleBean bean = new SimpleBean();
        IdentityBeanField beanField = newBeanField(SimpleBean.class.getDeclaredField("badValidatorValue"), false);

        assertThatExceptionOfType(CsvValidationException.class)
                .isThrownBy(() -> beanField.setFieldValue(bean, "value", "badValidatorValue"))
                .withMessageContaining(StringValidator.class.getName())
                .withMessageContaining("badValidatorValue");
    }

    @Test
    void reportsGetterFailureWhenReadingFieldForWrite() throws Exception {
        ThrowingGetterBean bean = new ThrowingGetterBean();
        IdentityBeanField beanField = newBeanField(ThrowingGetterBean.class.getDeclaredField("value"), false);

        assertThatExceptionOfType(CsvBeanIntrospectionException.class)
                .isThrownBy(() -> beanField.write(bean, null))
                .withCauseInstanceOf(ReflectiveOperationException.class)
                .withMessageContaining("value");
    }

    @Test
    void reportsRequiredFieldEmptyWhenWritingNullValue() throws Exception {
        SimpleBean bean = new SimpleBean();
        IdentityBeanField beanField = newBeanField(SimpleBean.class.getDeclaredField("requiredValue"), true);

        assertThatExceptionOfType(CsvRequiredFieldEmptyException.class)
                .isThrownBy(() -> beanField.write(bean, null));
    }

    private static IdentityBeanField newBeanField(Field field, boolean required) {
        return new IdentityBeanField(field.getDeclaringClass(), field, required, Locale.US);
    }

    public static class IdentityBeanField extends AbstractBeanField<Object, String> {
        public IdentityBeanField(Class<?> type, Field field, boolean required, Locale errorLocale) {
            super(type, field, required, errorLocale, null);
        }

        @Override
        protected Object convert(String value)
                throws CsvDataTypeMismatchException, CsvConstraintViolationException {
            return value;
        }
    }

    public static class SimpleBean {
        @PreAssignmentProcessor(processor = ConvertEmptyOrBlankStringsToDefault.class, paramString = "fallback")
        @PreAssignmentValidator(validator = MustMatchRegexExpression.class, paramString = "fallback")
        private String processedValue;

        private String requiredValue;

        @PreAssignmentProcessor(processor = StringProcessor.class)
        private String badProcessorValue;

        @PreAssignmentValidator(validator = StringValidator.class)
        private String badValidatorValue;
    }

    public static class ThrowingGetterBean {
        private String value = "unreachable";

        public String getValue() {
            throw new IllegalStateException("getter failed");
        }
    }
}
