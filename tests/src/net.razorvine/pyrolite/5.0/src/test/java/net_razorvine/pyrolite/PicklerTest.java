/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pyrolite;

import net.razorvine.pyro.Message;
import net.razorvine.pyro.PyroURI;
import net.razorvine.pyro.serializer.PyroSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PicklerTest {
    @Test
    void serpentSerializerRoundTripsPyroUriThroughRegisteredClassSerializer() throws Exception {
        PyroSerializer serializer = PyroSerializer.getSerpentSerializer();
        PyroURI endpoint = new PyroURI("worker", "example.test", 4444);

        byte[] serialized = serializer.serializeData(endpoint);
        Object deserialized = serializer.deserializeData(serialized);

        assertThat(serializer.getSerializerId()).isEqualTo(Message.SERIALIZER_SERPENT);
        assertThat(deserialized).isInstanceOfSatisfying(PyroURI.class, uri -> {
            assertThat(uri.protocol).isEqualTo("PYRO");
            assertThat(uri.objectid).isEqualTo("worker");
            assertThat(uri.host).isEqualTo("example.test");
            assertThat(uri.port).isEqualTo(4444);
        });
    }
}
