/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.gson.Gson;
import com.couchbase.client.core.deps.com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumTypeAdapterTest {
    @Test
    void mapsJson() {
        Gson gson = new Gson();
        assertEquals(State.READY, gson.fromJson("\"available\"", State.class));
        assertEquals("\"ready\"", gson.toJson(State.READY));
    }

    public enum State {
        @SerializedName(value = "ready", alternate = {"available"})
        READY,
        WAITING
    }
}
