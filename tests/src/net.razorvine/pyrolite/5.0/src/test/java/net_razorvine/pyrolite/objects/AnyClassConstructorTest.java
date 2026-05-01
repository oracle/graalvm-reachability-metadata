/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pyrolite.objects;

import net.razorvine.pyro.PyroURI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnyClassConstructorTest {
    @Test
    void parsesPyroUriIntoProtocolObjectIdHostAndPort() {
        PyroURI uri = new PyroURI("PYRO:worker@example.test:4444");

        assertThat(uri.protocol).isEqualTo("PYRO");
        assertThat(uri.objectid).isEqualTo("worker");
        assertThat(uri.host).isEqualTo("example.test");
        assertThat(uri.port).isEqualTo(4444);
        assertThat(uri).hasToString("<PyroURI PYRO:worker@example.test:4444>");
    }
}
