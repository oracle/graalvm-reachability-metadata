/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.log4j.chainsaw;

import java.io.IOException;
import java.net.ServerSocket;

public final class LoggingReceiverHarness {
    private LoggingReceiverHarness() {
    }

    public static boolean runWithReceiver(ReceiverClient client) throws Exception {
        int port = findAvailablePort();
        LoggingReceiver receiver = new LoggingReceiver(new MyTableModel(), port);
        receiver.start();

        return client.exchangeWithReceiver(port);
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public interface ReceiverClient {
        boolean exchangeWithReceiver(int port) throws Exception;
    }
}
