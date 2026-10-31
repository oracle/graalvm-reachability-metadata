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

public class ReflectiveTypeAdapterFactoryInnerRecordAdapterTest {
    @Test
    void mapsJson() {
        Gson gson = new Gson();
        Measurement value = gson.fromJson("""
                {"label":"depth","value":12.5}
                """, Measurement.class);
        assertEquals("depth", value.label());
        assertEquals(12.5, value.value());
    }

    public record Measurement(String label, double value) {
    }
}
