/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ArrayTypeAdapterTest {
    @Test
    void mapsJson() {
        Gson gson = new Gson();
        assertArrayEquals(new int[]{2, 3, 5}, gson.fromJson("[2,3,5]", int[].class));
        assertArrayEquals(new String[]{"alpha", "beta"}, gson.fromJson("""
                ["alpha","beta"]
                """, String[].class));
    }
}
