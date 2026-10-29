/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class UnsafeAllocatorTest {
    public static class Configuration {
        private final String name;

        public Configuration(String name) {
            this.name = name;
        }
    }

    @Test
    void deserializesClassesWithoutNoArgumentConstructors() {
        Configuration config = new Gson().fromJson("{\"name\":\"registry\"}", Configuration.class);
        assertEquals("registry", config.name);
    }
}
