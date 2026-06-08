/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.impl.CodecManager;
import io.vertx.core.eventbus.impl.codecs.ClusterSerializableCodec;
import io.vertx.core.shareddata.ClusterSerializable;
import io.vertx.core.spi.cluster.RegistrationInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ClusterSerializableCodecTest {

    @Test
    void decodeFromWireLoadsAllowedClusterSerializableClass() {
        CodecManager codecManager = new CodecManager();
        codecManager.clusterSerializableCheck(className -> className.equals(RegistrationInfo.class.getName()));
        ClusterSerializableCodec codec = new ClusterSerializableCodec(codecManager);
        RegistrationInfo original = new RegistrationInfo("node-a", 42L, true);
        Buffer wire = Buffer.buffer();

        codec.encodeToWire(wire, original);
        ClusterSerializable decoded = codec.decodeFromWire(0, wire);

        RegistrationInfo decodedRegistration = assertInstanceOf(RegistrationInfo.class, decoded);
        assertEquals(original, decodedRegistration);
    }
}
