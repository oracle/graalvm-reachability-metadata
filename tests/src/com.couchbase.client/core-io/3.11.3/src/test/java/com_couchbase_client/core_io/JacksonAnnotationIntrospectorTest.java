/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonAlias;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonProperty;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonAnnotationIntrospectorTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("\"ready\"", mapper.writeValueAsString(Status.READY));
        assertEquals(Status.READY, mapper.readValue("\"available\"", Status.class));
    }

    public enum Status {
        @JsonProperty("ready")
        @JsonAlias({"available", "ok"})
        READY,
        WAITING
    }
}
