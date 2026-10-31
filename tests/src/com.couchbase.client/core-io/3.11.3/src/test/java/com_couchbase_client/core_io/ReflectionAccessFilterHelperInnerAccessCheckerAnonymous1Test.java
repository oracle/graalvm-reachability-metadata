/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.gson.Gson;
import com.couchbase.client.core.deps.com.google.gson.GsonBuilder;
import com.couchbase.client.core.deps.com.google.gson.ReflectionAccessFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectionAccessFilterHelperInnerAccessCheckerAnonymous1Test {
    @Test
    void mapsJson() {
        Gson gson = new GsonBuilder()
                .addReflectionAccessFilter(type -> ReflectionAccessFilter.FilterResult.BLOCK_INACCESSIBLE)
                .create();
        PublicData value = gson.fromJson("""
                {"name":"readable","count":8}
                """, PublicData.class);
        assertEquals("readable", value.name);
        assertEquals(8, value.count);
        assertTrue(gson.toJson(value).contains("readable"));
    }

    public static class PublicData {
        public String name;
        public int count;
    }
}
