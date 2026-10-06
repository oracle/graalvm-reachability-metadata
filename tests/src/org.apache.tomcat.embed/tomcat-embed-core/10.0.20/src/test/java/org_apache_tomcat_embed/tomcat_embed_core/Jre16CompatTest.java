/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.apache.tomcat.util.compat.JreCompat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jre16CompatTest {

    @Test
    void exposesUnixDomainSocketOperationsThroughCompatibilityApi() throws Exception {
        JreCompat compat = JreCompat.getInstance();

        assertThat(compat.getUnixDomainSocketAddress("tomcat-compat.sock")).isNotNull();
        try (ServerSocketChannel server = compat.openUnixDomainServerSocketChannel();
                SocketChannel client = compat.openUnixDomainSocketChannel()) {
            assertThat(server.isOpen()).isTrue();
            assertThat(client.isOpen()).isTrue();
        }
    }
}
