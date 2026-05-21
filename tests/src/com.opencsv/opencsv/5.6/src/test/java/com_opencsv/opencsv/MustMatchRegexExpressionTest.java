/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.BeanFieldSingleValue;
import com.opencsv.bean.validators.MustMatchRegexExpression;
import com.opencsv.exceptions.CsvValidationException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MustMatchRegexExpressionTest {
    @Test
    void reportsLocalizedValidationFailureForMismatchedValue() throws Exception {
        MustMatchRegexExpression validator = new MustMatchRegexExpression();
        validator.setParameterString("[0-9]{3}");
        BeanFieldSingleValue<RegexBean, String> beanField = new BeanFieldSingleValue<>(
                RegexBean.class,
                RegexBean.class.getField("code"),
                false,
                Locale.US,
                null,
                "",
                "");

        assertThatThrownBy(() -> validator.validate("ABC", beanField))
                .isInstanceOf(CsvValidationException.class)
                .hasMessage("Field code value \"ABC\" did not match expected format of [0-9]{3}");
    }

    public static class RegexBean {
        public String code;
    }
}
