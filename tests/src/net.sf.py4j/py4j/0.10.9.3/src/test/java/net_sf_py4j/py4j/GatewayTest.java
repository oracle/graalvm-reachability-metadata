/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_py4j.py4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import py4j.Gateway;

public class GatewayTest {
    @Test
    void createsProxyForPythonCallbackInterface() {
        Gateway gateway = new Gateway(null);
        Class<?>[] interfacesToImplement = { PythonCallback.class };

        Object proxy = gateway.createProxy(GatewayTest.class.getClassLoader(), interfacesToImplement, "callback-1");

        assertThat(proxy).isInstanceOf(PythonCallback.class);
    }

    public interface PythonCallback {
        String call(String value);
    }
}
