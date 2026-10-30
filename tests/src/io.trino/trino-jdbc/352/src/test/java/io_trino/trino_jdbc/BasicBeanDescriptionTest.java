/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.annotation.JsonInclude;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BasicBeanDescriptionTest {
    @Test
    void comparesAgainstDefaultBeanForNonDefaultInclusion() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Settings settings = new Settings();
        assertEquals("{}", mapper.writeValueAsString(settings));
        settings.retries = 5;
        assertEquals("{\"retries\":5}", mapper.writeValueAsString(settings));
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class Settings {
        public int retries = 3;
    }
}
