/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencsv.CSVReader;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import java.io.StringReader;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class ColumnPositionMappingStrategyTest {
    @Test
    void captureHeaderRejectsUnsetType() throws Exception {
        ColumnPositionMappingStrategy<RequiredPositionBean> strategy = new ColumnPositionMappingStrategy<>();
        strategy.setErrorLocale(Locale.ROOT);

        try (CSVReader reader = new CSVReader(new StringReader("present\n"))) {
            assertThrows(IllegalStateException.class, () -> strategy.captureHeader(reader));
        }
    }

    @Test
    void parsingCapturesMissingRequiredPosition() {
        CsvToBean<RequiredPositionBean> csvToBean = new CsvToBeanBuilder<RequiredPositionBean>(
                new StringReader("present\n"))
                .withType(RequiredPositionBean.class)
                .withErrorLocale(Locale.ROOT)
                .withThrowExceptions(false)
                .build();

        List<RequiredPositionBean> beans = csvToBean.parse();

        assertEquals(0, beans.size());
        List<CsvException> capturedExceptions = csvToBean.getCapturedExceptions();
        assertFalse(capturedExceptions.isEmpty());
        assertTrue(capturedExceptions.get(0) instanceof CsvRequiredFieldEmptyException);
    }

    public static class RequiredPositionBean {
        @CsvBindByPosition(position = 1, required = true)
        private String requiredValue;

        public String getRequiredValue() {
            return requiredValue;
        }

        public void setRequiredValue(String requiredValue) {
            this.requiredValue = requiredValue;
        }
    }
}
