/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.mail.util.SocketFetcher;
import java.net.Socket;
import java.util.Properties;
import javax.net.SocketFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class SocketFetcherTest {
    @Test
    void opensConnectionUsingConfiguredFactory() throws Exception {
        exchange(false);
    }

    @Test
    void opensConnectionWithoutContextClassLoader() throws Exception {
        exchange(true);
    }

    private static void exchange(boolean clearContextLoader) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try (LocalMailServer server = new LocalMailServer((input, output) -> {
            assertEquals("request", input.readLine());
            LocalMailServer.reply(output, "response");
        })) {
            if (clearContextLoader) {
                thread.setContextClassLoader(null);
            }
            Properties properties = new Properties();
            properties.setProperty("socket.connectiontimeout", "10000");
            properties.setProperty("socket.timeout", "10000");
            properties.setProperty("socket.socketFactory.class", SocketFactory.class.getName());
            properties.setProperty("socket.socketFactory.fallback", "false");
            try (Socket socket = SocketFetcher.getSocket(server.host(), server.port(), properties, "socket")) {
                socket.getOutputStream().write(new byte[] {'r', 'e', 'q', 'u', 'e', 's', 't', '\r', '\n'});
                socket.getOutputStream().flush();
                assertEquals('r', socket.getInputStream().read());
                assertEquals(10000, socket.getSoTimeout());
            }
            server.verify();
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
