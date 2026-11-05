/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
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
    void exchangesDataOverUnixDomainChannels() throws Exception {
        JreCompat compatibility = JreCompat.getInstance();
        SocketAddress address = compatibility.getUnixDomainSocketAddress(directory.resolve("endpoint").toString());
        try (ServerSocketChannel server = compatibility.openUnixDomainServerSocketChannel();
                SocketChannel client = compatibility.openUnixDomainSocketChannel()) {
            server.configureBlocking(false);
            server.bind(address);
            client.configureBlocking(false);
            client.connect(address);
            long deadline = System.nanoTime() + 30_000_000_000L;
            while (!client.finishConnect()) {
                checkDeadline(deadline);
                Thread.sleep(10);
            }
            SocketChannel accepted = server.accept();
            while (accepted == null) {
                checkDeadline(deadline);
                Thread.sleep(10);
                accepted = server.accept();
            }
            try (SocketChannel connection = accepted) {
                connection.configureBlocking(false);
                ByteBuffer outgoing = ByteBuffer.wrap(new byte[] {42});
                while (outgoing.hasRemaining()) {
                    checkDeadline(deadline);
                    client.write(outgoing);
                }
                ByteBuffer incoming = ByteBuffer.allocate(1);
                while (incoming.hasRemaining()) {
                    checkDeadline(deadline);
                    connection.read(incoming);
                }
                assertThat(incoming.array()).containsExactly((byte) 42);
            }
        }
    }

    private static void checkDeadline(long deadline) {
        assertThat(System.nanoTime()).as("Unix domain socket exchange deadline").isLessThan(deadline);
    }
}
