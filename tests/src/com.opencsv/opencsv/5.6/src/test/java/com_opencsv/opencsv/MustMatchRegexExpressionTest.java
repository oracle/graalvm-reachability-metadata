/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.validators.MustMatchRegexExpression;
import com.opencsv.bean.validators.PreAssignmentValidator;
import com.opencsv.exceptions.CsvValidationException;
import java.io.StringReader;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class MustMatchRegexExpressionTest {
    @Test
    void reportsLocalizedValidationExceptionForNonMatchingInput() {
        CsvToBean<CodeBean> csvToBean = new CsvToBeanBuilder<CodeBean>(new StringReader("code\nabc\n"))
                .withType(CodeBean.class)
                .withThrowExceptions(false)
                .withErrorLocale(Locale.US)
                .build();

        assertThat(csvToBean.iterator().hasNext()).isFalse();
        assertThat(csvToBean.getCapturedExceptions())
                .singleElement()
                .isInstanceOfSatisfying(CsvValidationException.class, exception -> assertThat(exception)
                        .hasMessageContaining("code")
                        .hasMessageContaining("abc")
                        .hasMessageContaining("^[A-Z]{3}$"));
    }

    public static class CodeBean {
        @CsvBindByName(column = "code")
        @PreAssignmentValidator(validator = MustMatchRegexExpression.class, paramString = "^[A-Z]{3}$")
        private String code;
    }
}
