/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.java_client;

import com.couchbase.client.java.codec.SerializableTranscoder;
import com.couchbase.client.java.codec.Transcoder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SerializableTranscoderTest {
    @Test
    void roundTripsSerializableValue() {
        ArrayList<String> original = new ArrayList<>(List.of("first", "second", "third"));

        Transcoder.EncodedValue encoded = SerializableTranscoder.INSTANCE.encode(original);
        ArrayList<?> decoded = SerializableTranscoder.INSTANCE.decode(
                ArrayList.class, encoded.encoded(), encoded.flags());

        assertTrue(encoded.encoded().length > 0);
        assertEquals(original, decoded);
        assertNotSame(original, decoded);
    }
}
