/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.core.config.plugins.convert.DateTypeConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DateTypeConverterTest {
    @Test
    void constructsEachSupportedDateTypeFromMilliseconds() {
        List<Class<? extends Date>> types = List.of(Date.class, java.sql.Date.class, Time.class, Timestamp.class);
        for (Class<? extends Date> type : types) {
            Date converted = DateTypeConverter.fromMillis(123456789L, type);
            assertNotNull(converted);
            assertEquals(type, converted.getClass());
            assertEquals(123456789L, converted.getTime());
        }
    }
}
