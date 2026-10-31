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

public class UnsafeAllocatorAnonymous1Test {
    @Test
    void mapsJson() {
        Gson gson = new Gson();
        Entry entry = gson.fromJson("""
                {"name":"from-json"}
                """, Entry.class);
        assertEquals("from-json", entry.name);
        assertEquals(0, entry.constructorValue);
    }

    public static class Entry {
        public String name;
        public int constructorValue;

        public Entry(String name) {
            this.name = name;
            constructorValue = 99;
        }
    }
}
