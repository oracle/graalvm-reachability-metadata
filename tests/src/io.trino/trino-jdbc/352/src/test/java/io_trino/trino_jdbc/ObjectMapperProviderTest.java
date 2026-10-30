/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.airlift.json.ObjectMapperProvider;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import io.trino.jdbc.$internal.joda.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectMapperProviderTest {
    @Test
    void registersOptionalAndDateModules() throws Exception {
        ObjectMapper mapper = new ObjectMapperProvider().get();
        LocalDate day = mapper.readValue("\"2021-04-09\"", LocalDate.class);
        assertEquals(2021, day.getYear());
        assertEquals(4, day.getMonthOfYear());
        assertEquals("\"2021-04-09\"", mapper.writeValueAsString(day));
        assertEquals("\"present\"", mapper.writeValueAsString(Optional.of("present")));
    }
}
