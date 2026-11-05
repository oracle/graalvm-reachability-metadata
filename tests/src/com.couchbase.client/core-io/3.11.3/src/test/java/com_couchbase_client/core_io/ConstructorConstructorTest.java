/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConstructorConstructorTest {
    @Test
    void mapsJson() {
        Gson gson = new Gson();
        Settings settings = gson.fromJson("""
                {"name":"custom"}
                """, Settings.class);
        assertEquals("custom", settings.name);
        assertEquals(11, settings.attempts);
    }

    public static class Settings {
        public String name;
        public int attempts;

        public Settings() {
            attempts = 11;
        }
    }
}
