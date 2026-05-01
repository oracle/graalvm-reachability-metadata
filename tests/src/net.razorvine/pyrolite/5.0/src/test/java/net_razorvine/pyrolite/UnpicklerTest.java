/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pyrolite;

import java.util.Set;

import net.razorvine.pyro.PyroProxy;
import net.razorvine.pyro.serializer.PyroSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnpicklerTest {
    @Test
    void serpentSerializerRoundTripsPyroProxyMetadataAndHandshake() throws Exception {
        PyroProxy proxy = new PyroProxy("example.test", 4444, "worker");
        proxy.pyroHandshake = "hello from java";
        proxy.pyroMethods.add("run");
        proxy.pyroAttrs.add("status");
        proxy.pyroOneway.add("notify");
        PyroSerializer serializer = PyroSerializer.getSerpentSerializer();

        byte[] serialized = serializer.serializeData(proxy);
        Object deserialized = serializer.deserializeData(serialized);

        assertThat(deserialized).isInstanceOfSatisfying(PyroProxy.class, restored -> {
            assertThat(restored.hostname).isEqualTo("example.test");
            assertThat(restored.port).isEqualTo(4444);
            assertThat(restored.objectid).isEqualTo("worker");
            assertThat(restored.pyroHandshake).isEqualTo("hello from java");
            assertThat(restored.pyroMethods).containsExactlyInAnyOrderElementsOf(Set.of("run"));
            assertThat(restored.pyroAttrs).containsExactlyInAnyOrderElementsOf(Set.of("status"));
            assertThat(restored.pyroOneway).containsExactlyInAnyOrderElementsOf(Set.of("notify"));
        });
    }
}
