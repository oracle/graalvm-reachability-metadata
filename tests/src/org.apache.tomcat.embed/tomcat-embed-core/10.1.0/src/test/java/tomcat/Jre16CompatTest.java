/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

import org.apache.tomcat.util.compat.JreCompat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class Jre16CompatTest {
    @TempDir
    Path directory;

    @Test
    void opensAndBindsUnixDomainChannels() throws Exception {
        JreCompat compat = JreCompat.getInstance();
        SocketAddress address = compat.getUnixDomainSocketAddress(directory.resolve("server.sock").toString());
        try (ServerSocketChannel server = compat.openUnixDomainServerSocketChannel();
                SocketChannel client = compat.openUnixDomainSocketChannel()) {
            server.bind(address);
            client.bind(compat.getUnixDomainSocketAddress(directory.resolve("client.sock").toString()));
            assertThat(server.getLocalAddress()).isEqualTo(address);
            assertThat(client.getLocalAddress().toString()).endsWith("client.sock");
        }
    }
}
