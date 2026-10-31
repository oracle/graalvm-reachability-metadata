/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectDecoderInputStreamTest {
    @Test
    void exercisesPublicApi() throws Exception {
        HashMap<String, String> values = new HashMap<>(Map.of("direction", "inbound"));
        assertEquals(values, NettySerializationSupport.throughStreams(values));
    }
}
