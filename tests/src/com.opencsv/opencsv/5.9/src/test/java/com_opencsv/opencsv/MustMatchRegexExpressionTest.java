/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.validators.MustMatchRegexExpression;
import com.opencsv.bean.validators.PreAssignmentValidator;
import com.opencsv.exceptions.CsvValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class MustMatchRegexExpressionTest {
    @Test
    void validatesAnnotatedInputBeforeAssigningItToBean() throws Exception {
        HeaderColumnNameMappingStrategy<Product> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(Product.class);
        strategy.setErrorLocale(Locale.US);
        try (CSVReader reader = new CSVReader(new StringReader("code\n"))) {
            strategy.captureHeader(reader);
        }
        assertThat(strategy.populateNewBean(new String[] {"AB-123"}).code).isEqualTo("AB-123");
        assertThatThrownBy(() -> strategy.populateNewBean(new String[] {"invalid"}))
                .isInstanceOf(CsvValidationException.class)
                .hasMessageContaining("code")
                .hasMessageContaining("invalid")
                .hasMessageContaining("[A-Z]{2}-[0-9]{3}");
    }

    public static class Product {
        @CsvBindByName
        @PreAssignmentValidator(validator = MustMatchRegexExpression.class, paramString = "[A-Z]{2}-[0-9]{3}")
        public String code;
    }
}
