/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JDK14UtilInnerRecordAccessorTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Shipment original = new Shipment("box", 6);
        String json = mapper.writeValueAsString(original);
        assertEquals(original, mapper.readValue(json, Shipment.class));
        assertEquals(6, mapper.readTree(json).get("count").asInt());
    }

    public record Shipment(String name, int count) {
    }
}
