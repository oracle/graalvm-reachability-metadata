/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;

import javassist.util.HotSwapper;

import org.junit.jupiter.api.Test;

public class HotSwapperTest {
    @Test
    void reportsConnectionFailureForUnavailableDebuggerPort() throws Exception {
        int port = unusedLocalPort();

        assertThatThrownBy(() -> new HotSwapper(port))
                .isInstanceOf(IOException.class);
    }

    private static int unusedLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
