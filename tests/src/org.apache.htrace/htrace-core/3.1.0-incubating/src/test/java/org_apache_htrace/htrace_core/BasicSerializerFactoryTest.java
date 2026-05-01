/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import java.sql.Date;

import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicSerializerFactoryTest {
    @Test
    void serializesSqlDateWithLazyStandardSerializer() throws Exception {
        Date sqlDate = Date.valueOf("2024-03-14");
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(sqlDate);

        assertThat(json).isEqualTo('"' + sqlDate.toString() + '"');
    }
}
