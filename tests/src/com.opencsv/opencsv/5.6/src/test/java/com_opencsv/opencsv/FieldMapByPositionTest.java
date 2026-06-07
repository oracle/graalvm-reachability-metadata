/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencsv.bean.CsvBindAndJoinByPosition;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import java.io.StringWriter;
import java.util.Locale;
import org.apache.commons.collections4.MultiValuedMap;
import org.junit.jupiter.api.Test;

public class FieldMapByPositionTest {
    @Test
    void writeReportsRequiredJoinedHeaderMissingFromNullMap() {
        StringWriter writer = new StringWriter();
        StatefulBeanToCsv<RequiredJoinedPositionsBean> beanToCsv =
                new StatefulBeanToCsvBuilder<RequiredJoinedPositionsBean>(writer)
                        .withErrorLocale(Locale.ROOT)
                        .build();

        CsvRequiredFieldEmptyException exception = assertThrows(
                CsvRequiredFieldEmptyException.class,
                () -> beanToCsv.write(new RequiredJoinedPositionsBean()));

        assertEquals(RequiredJoinedPositionsBean.class, exception.getBeanClass());
        assertEquals("values", exception.getDestinationField().getName());
        assertTrue(exception.getMessage().contains("Header is missing required fields [values]."));
        assertEquals("", writer.toString());
    }

    public static class RequiredJoinedPositionsBean {
        @CsvBindAndJoinByPosition(position = "1-2", elementType = String.class, required = true)
        private MultiValuedMap<Integer, String> values;
    }
}
