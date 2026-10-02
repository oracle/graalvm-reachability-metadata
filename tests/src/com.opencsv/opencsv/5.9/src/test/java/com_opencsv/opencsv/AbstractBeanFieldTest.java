/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.BeanField;
import com.opencsv.bean.processor.PreAssignmentProcessor;
import com.opencsv.bean.processor.StringProcessor;
import com.opencsv.bean.validators.PreAssignmentValidator;
import com.opencsv.bean.validators.StringValidator;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import com.opencsv.exceptions.CsvValidationException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractBeanFieldTest {

    @Test
    void setFieldValueRunsPreAssignmentProcessorAndValidatorBeforeConversion() throws Exception {
        ProcessingBean bean = new ProcessingBean();
        PlainBeanField field = beanField(ProcessingBean.class, "value", false);

        field.setFieldValue(bean, "source", "value");

        assertThat(bean.value).isEqualTo("prefix-source-converted");
    }

    @Test
    void setFieldValueRejectsBlankRequiredValues() throws Exception {
        PlainBeanField field = beanField(PlainBean.class, "value", true);
        PlainBean bean = new PlainBean();

        assertThatThrownBy(() -> field.setFieldValue(bean, " ", "value"))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .hasMessageContaining("value");
    }

    @Test
    void setFieldValueReportsPreAssignmentProcessorInstantiationProblems() throws Exception {
        PlainBeanField field = beanField(BadProcessorBean.class, "value", false);
        BadProcessorBean bean = new BadProcessorBean();

        assertThatThrownBy(() -> field.setFieldValue(bean, "source", "value"))
                .isInstanceOf(CsvValidationException.class)
                .hasMessageContaining(NoDefaultConstructorProcessor.class.getName())
                .hasMessageContaining("value");
    }

    @Test
    void setFieldValueReportsPreAssignmentValidatorInstantiationProblems() throws Exception {
        PlainBeanField field = beanField(BadValidatorBean.class, "value", false);
        BadValidatorBean bean = new BadValidatorBean();

        assertThatThrownBy(() -> field.setFieldValue(bean, "source", "value"))
                .isInstanceOf(CsvValidationException.class)
                .hasMessageContaining(NoDefaultConstructorValidator.class.getName())
                .hasMessageContaining("value");
    }

    @Test
    void getFieldValueReportsGetterFailures() throws Exception {
        PlainBeanField field = beanField(ThrowingGetterBean.class, "value", false);

        assertThatThrownBy(() -> field.getFieldValue(new ThrowingGetterBean()))
                .isInstanceOf(CsvBeanIntrospectionException.class)
                .hasMessageContaining("value")
                .hasMessageContaining(ThrowingGetterBean.class.toString());
    }

    @Test
    void writeRejectsEmptyRequiredFieldValues() throws Exception {
        PlainBeanField field = beanField(PlainBean.class, "value", true);
        PlainBean bean = new PlainBean();

        assertThatThrownBy(() -> field.write(bean, null))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .hasMessageContaining("value");
    }

    private static PlainBeanField beanField(Class<?> beanType, String fieldName, boolean required)
            throws NoSuchFieldException {
        Field field = beanType.getField(fieldName);
        PlainBeanField beanField = new PlainBeanField();
        beanField.setType(beanType);
        beanField.setField(field);
        beanField.setRequired(required);
        beanField.setErrorLocale(Locale.US);
        return beanField;
    }

    public static class PlainBeanField extends AbstractBeanField<Object, Object> {
        @Override
        protected Object convert(String value)
                throws CsvDataTypeMismatchException, CsvConstraintViolationException {
            return value == null ? null : value + "-converted";
        }
    }

    public static class PlainBean {
        public String value;
    }

    public static class ProcessingBean {
        @PreAssignmentProcessor(processor = PrefixProcessor.class, paramString = "prefix-")
        @PreAssignmentValidator(validator = PrefixValidator.class, paramString = "prefix-")
        public String value;
    }

    public static class BadProcessorBean {
        @PreAssignmentProcessor(processor = NoDefaultConstructorProcessor.class)
        public String value;
    }

    public static class BadValidatorBean {
        @PreAssignmentValidator(validator = NoDefaultConstructorValidator.class)
        public String value;
    }

    public static class ThrowingGetterBean {
        public String value = "unread";

        public String getValue() {
            throw new IllegalStateException("Cannot read value");
        }
    }

    public static class PrefixProcessor implements StringProcessor {
        private String prefix;

        @Override
        public String processString(String value) {
            return prefix + value;
        }

        @Override
        public void setParameterString(String value) {
            this.prefix = value;
        }
    }

    public static class PrefixValidator implements StringValidator {
        private String requiredPrefix;

        @Override
        public boolean isValid(String value) {
            return value != null && value.startsWith(requiredPrefix);
        }

        @Override
        public void validate(String value, BeanField field) throws CsvValidationException {
            if (!isValid(value)) {
                throw new CsvValidationException("Value does not start with " + requiredPrefix);
            }
        }

        @Override
        public void setParameterString(String value) {
            this.requiredPrefix = value;
        }
    }

    public static class NoDefaultConstructorProcessor implements StringProcessor {
        public NoDefaultConstructorProcessor(String ignored) {
        }

        @Override
        public String processString(String value) {
            return value;
        }

        @Override
        public void setParameterString(String value) {
        }
    }

    public static class NoDefaultConstructorValidator implements StringValidator {
        public NoDefaultConstructorValidator(String ignored) {
        }

        @Override
        public boolean isValid(String value) {
            return true;
        }

        @Override
        public void validate(String value, BeanField field) throws CsvValidationException {
        }

        @Override
        public void setParameterString(String value) {
        }
    }
}
