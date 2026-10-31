/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.core.deps.io.netty.buffer.ByteBufInputStream;
import com.couchbase.client.core.deps.io.netty.channel.embedded.EmbeddedChannel;
import com.couchbase.client.core.deps.io.netty.handler.codec.serialization.CompatibleObjectEncoder;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompatibleObjectEncoderTest {
    @Test
    void exercisesPublicApi() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new CompatibleObjectEncoder());
        ArrayList<String> original = new ArrayList<>(List.of("compatible", "stream"));
        try {
            assertTrue(channel.writeOutbound(original));
            ByteBuf encoded = channel.readOutbound();
            try (ObjectInputStream input = new ObjectInputStream(new ByteBufInputStream(encoded, true))) {
                assertEquals(original, input.readObject());
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
