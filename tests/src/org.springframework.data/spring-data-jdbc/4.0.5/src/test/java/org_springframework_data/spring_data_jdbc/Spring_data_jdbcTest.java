/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
public class Spring_data_jdbcTest {
    @Test
    void convertsLocalDateTimeToJdbcTimestampAndBack() {
        JdbcCustomConversions conversions = new JdbcCustomConversions();
        DefaultConversionService service = new DefaultConversionService();
        conversions.registerConvertersIn(service);
        LocalDateTime value = LocalDateTime.of(2024, 6, 15, 10, 20, 30, 123456789);

        Timestamp timestamp = service.convert(value, Timestamp.class);

        assertEquals(Timestamp.valueOf(value), timestamp);
        assertEquals(value, service.convert(timestamp, LocalDateTime.class));
    }
}
