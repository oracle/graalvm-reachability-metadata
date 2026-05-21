/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

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
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractBeanFieldTest {
    @Test
    void appliesPreAssignmentProcessorAndValidatorBeforeSettingField() throws Exception {
        IdentityBeanField beanField = beanFieldFor(ProcessedBean.class, "value", false);
        ProcessedBean bean = new ProcessedBean();

        beanField.setFieldValue(bean, " ", "value");

        assertThat(bean.value).isEqualTo("fallback");
    }

    @Test
    void reportsRequiredEmptyFieldOnRead() throws Exception {
        IdentityBeanField beanField = beanFieldFor(RequiredReadBean.class, "value", true);

        assertThatThrownBy(() -> beanField.setFieldValue(new RequiredReadBean(), " ", "value"))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .hasMessageContaining("value");
    }

    @Test
    void reportsProcessorThatCannotBeInstantiated() throws Exception {
        IdentityBeanField beanField = beanFieldFor(InvalidProcessorBean.class, "value", false);

        assertThatThrownBy(() -> beanField.setFieldValue(new InvalidProcessorBean(), "value", "value"))
                .isInstanceOf(CsvValidationException.class)
                .hasMessageContaining(StringProcessor.class.getName());
    }

    @Test
    void reportsValidatorThatCannotBeInstantiated() throws Exception {
        IdentityBeanField beanField = beanFieldFor(InvalidValidatorBean.class, "value", false);

        assertThatThrownBy(() -> beanField.setFieldValue(new InvalidValidatorBean(), "value", "value"))
                .isInstanceOf(CsvValidationException.class)
                .hasMessageContaining(StringValidator.class.getName());
    }

    @Test
    void reportsGetterFailureWhileReadingFieldValue() throws Exception {
        IdentityBeanField beanField = beanFieldFor(ThrowingGetterBean.class, "value", false);

        assertThatThrownBy(() -> beanField.getFieldValue(new ThrowingGetterBean()))
                .isInstanceOf(CsvBeanIntrospectionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("value");
    }

    @Test
    void reportsRequiredEmptyFieldOnWrite() throws Exception {
        IdentityBeanField beanField = beanFieldFor(RequiredWriteBean.class, "value", true);

        assertThatThrownBy(() -> beanField.write(new RequiredWriteBean(), "value"))
                .isInstanceOf(CsvRequiredFieldEmptyException.class)
                .hasMessageContaining("value");
    }

    private static IdentityBeanField beanFieldFor(Class<?> beanType, String fieldName, boolean required)
            throws NoSuchFieldException {
        IdentityBeanField beanField = new IdentityBeanField();
        beanField.setType(beanType);
        beanField.setField(beanType.getDeclaredField(fieldName));
        beanField.setRequired(required);
        beanField.setErrorLocale(Locale.US);
        return beanField;
    }

    public static class IdentityBeanField extends AbstractBeanField<Object, String> {
        @Override
        protected Object convert(String value) throws CsvDataTypeMismatchException, CsvConstraintViolationException {
            return value;
        }
    }

    public static class ProcessedBean {
        @PreAssignmentProcessor(processor = ConvertEmptyOrBlankStringsToDefault.class, paramString = "fallback")
        @PreAssignmentValidator(validator = MustMatchRegexExpression.class, paramString = "fallback")
        public String value;
    }

    public static class RequiredReadBean {
        public String value;
    }

    public static class InvalidProcessorBean {
        @PreAssignmentProcessor(processor = StringProcessor.class)
        public String value;
    }

    public static class InvalidValidatorBean {
        @PreAssignmentValidator(validator = StringValidator.class)
        public String value;
    }

    public static class ThrowingGetterBean {
        public String value;

        public String getValue() {
            throw new IllegalStateException("getter failed");
        }
    }

    public static class RequiredWriteBean {
        public String value;
    }
}
