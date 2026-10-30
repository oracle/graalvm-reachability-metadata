/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FieldPropertyTest {
    @Test
    void bindsAndUpdatesPublicFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Account account = mapper.readValue("{\"name\":\"Ada\",\"balance\":42}", Account.class);
        assertEquals("Ada", account.name);
        assertEquals(42, account.balance);
        mapper.readerForUpdating(account).readValue("{\"balance\":73}");
        assertEquals(73, account.balance);
    }

    public static class Account {
        public String name;
        public int balance;
    }
}
