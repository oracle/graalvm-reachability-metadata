/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pyrolite;

import java.io.Serializable;
import java.util.Map;

import net.razorvine.pickle.Pickler;
import net.razorvine.pickle.Unpickler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PicklerTest {
    @Test
    void picklesSerializableJavaBeanThroughPublicGetters() throws Exception {
        SerializableEndpoint endpoint = new SerializableEndpoint("worker", true, "PYRO:worker@example.test:4444");

        byte[] pickle = new Pickler().dumps(endpoint);
        Object unpickled = new Unpickler().loads(pickle);

        assertThat(unpickled).isInstanceOfSatisfying(Map.class, rawMap -> {
            Map<?, ?> map = (Map<?, ?>) rawMap;
            assertThat(map.get("name")).isEqualTo("worker");
            assertThat(map.get("active")).isEqualTo(true);
            assertThat(map.get("URI")).isEqualTo("PYRO:worker@example.test:4444");
            assertThat(map.get("__class__")).isEqualTo(SerializableEndpoint.class.getName());
        });
    }

    public static final class SerializableEndpoint implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final boolean active;
        private final String uri;

        public SerializableEndpoint(String name, boolean active, String uri) {
            this.name = name;
            this.active = active;
            this.uri = uri;
        }

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }

        public String getURI() {
            return uri;
        }
    }
}
