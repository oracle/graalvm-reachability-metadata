/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;

import javax.net.ssl.SSLServerSocket;

import org.hsqldb.server.HsqlSocketFactory;
import org.hsqldb.server.HsqlSocketFactorySecure;
import org.junit.jupiter.api.Test;

public class HsqlSocketFactoryTest {
    @Test
    void getInstanceCreatesSecureSocketFactory() throws Exception {
        HsqlSocketFactory factory = HsqlSocketFactory.getInstance(true);

        assertThat(factory).isInstanceOf(HsqlSocketFactory.class);
        assertThat(factory).isInstanceOf(HsqlSocketFactorySecure.class);
        assertThat(factory.getClass()).isEqualTo(HsqlSocketFactorySecure.class);
        assertThat(factory.isSecure()).isTrue();

        try (ServerSocket socket = factory.createServerSocket(0)) {
            assertThat(socket).isInstanceOf(SSLServerSocket.class);
            assertThat(socket.isBound()).isTrue();
        }
    }
}
