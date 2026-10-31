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
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(120)
public class CsvBeanIntrospectionExceptionTest {
    @Test
    void identifiesBeanAndFieldWhenSetterRejectsAssignment() throws Exception {
        HeaderColumnNameMappingStrategy<Account> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setType(Account.class);
        strategy.setErrorLocale(Locale.US);
        try (CSVReader reader = new CSVReader(new StringReader("name\n"))) {
            strategy.captureHeader(reader);
        }
        CsvBeanIntrospectionException exception = assertThrows(CsvBeanIntrospectionException.class,
                () -> strategy.populateNewBean(new String[] {""}));
        assertThat(exception.getBean()).isInstanceOf(Account.class);
        assertThat(exception.getField().getName()).isEqualTo("name");
        assertThat(exception.getMessage()).contains("name", "Account");
        assertThat(exception.getLocalizedMessage()).isNotBlank();
    }

    public static class Account {
        @CsvBindByName
        public String name;

        public void setName(String name) {
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Account name must not be empty");
            }
            this.name = name;
        }
    }
}
