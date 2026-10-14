/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

import org.apache.tomcat.util.compat.JreCompat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class Jre16CompatTest {

    @Test
    void createsUnixDomainAddressAndChannels(@TempDir Path directory) throws Exception {
        JreCompat compatibility = JreCompat.getInstance();
        SocketAddress address = compatibility.getUnixDomainSocketAddress(directory.resolve("tomcat.sock").toString());

        try (ServerSocketChannel serverChannel = compatibility.openUnixDomainServerSocketChannel();
                SocketChannel socketChannel = compatibility.openUnixDomainSocketChannel()) {
            assertThat(address).hasToString(directory.resolve("tomcat.sock").toString());
            assertThat(serverChannel.isOpen()).isTrue();
            assertThat(socketChannel.isOpen()).isTrue();
        }
    }
}
