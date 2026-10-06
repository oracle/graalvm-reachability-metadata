/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.java_client;

import com.couchbase.client.java.codec.Sdk2CompatibleLegacyTranscoder;
import com.couchbase.client.java.codec.Transcoder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Sdk2CompatibleLegacyTranscoderTest {
    @Test
    void roundTripsSerializableValue() {
        Sdk2CompatibleLegacyTranscoder transcoder = new Sdk2CompatibleLegacyTranscoder();
        ArrayList<String> original = new ArrayList<>(List.of("alpha", "beta", "gamma"));

        Transcoder.EncodedValue encoded = transcoder.encode(original);
        ArrayList<?> decoded = transcoder.decode(ArrayList.class, encoded.encoded(), encoded.flags());

        assertTrue(encoded.encoded().length > 0);
        assertEquals(original, decoded);
        assertNotSame(original, decoded);
    }
}
