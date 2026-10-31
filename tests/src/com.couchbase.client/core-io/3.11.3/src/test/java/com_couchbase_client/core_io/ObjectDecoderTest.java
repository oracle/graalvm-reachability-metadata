/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectDecoderTest {
    @Test
    void exercisesPublicApi() throws Exception {
        LinkedHashMap<String, Integer> original = new LinkedHashMap<>(Map.of("answer", 42));
        assertEquals(original, NettySerializationSupport.throughChannels(original));
    }
}
