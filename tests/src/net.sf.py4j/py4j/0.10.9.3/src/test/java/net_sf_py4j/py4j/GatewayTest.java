/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_py4j.py4j;

import org.junit.jupiter.api.Test;

import py4j.Gateway;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayTest {
    @Test
    void createsProxyForPythonBackedInterface() {
        Gateway gateway = new Gateway(new Object());
        Class<?>[] interfacesToImplement = {PythonBackedGreeter.class, PythonBackedMarker.class};

        Object proxy = gateway.createProxy(GatewayTest.class.getClassLoader(), interfacesToImplement, "python-object");

        assertThat(proxy).isInstanceOf(PythonBackedGreeter.class);
        assertThat(proxy).isInstanceOf(PythonBackedMarker.class);
    }

    public interface PythonBackedGreeter {
        String greet(String name);
    }

    public interface PythonBackedMarker {
    }
}
