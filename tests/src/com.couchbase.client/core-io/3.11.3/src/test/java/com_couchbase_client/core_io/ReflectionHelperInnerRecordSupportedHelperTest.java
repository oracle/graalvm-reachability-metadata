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

public class ReflectionHelperInnerRecordSupportedHelperTest {
    @Test
    void mapsJson() {
        Gson gson = new Gson();
        Address original = new Address("Oak Road", 42);
        assertEquals(original, gson.fromJson(gson.toJson(original), Address.class));
    }

    public record Address(String street, int number) {
    }
}
