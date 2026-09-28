/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Exercises the public connection path that selects the driver's SSPI implementation.
 */
public class ConnectionFactoryImplTest {

    @Test
    void connectionWithSspiAuthenticationLoadsSspiClient() throws Exception {
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(15_000);
            Thread serverThread = new Thread(() -> sendSspiAuthenticationRequest(server, serverFailure));
            serverThread.start();

            Properties properties = new Properties();
            properties.setProperty("user", "test");
            properties.setProperty("gsslib", "sspi");
            properties.setProperty("sslmode", "disable");
            properties.setProperty("gssEncMode", "disable");
            properties.setProperty("connectTimeout", "10");

            PSQLException exception = catchThrowableOfType(
                    () -> DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + server.getLocalPort()
                            + "/test", properties), PSQLException.class);

            assertThat((Throwable) exception).isNotNull();
            assertThat(exception.getSQLState()).isEqualTo(PSQLState.CONNECTION_UNABLE_TO_CONNECT.getState());
            serverThread.join(15_000);
            assertThat(serverThread.isAlive()).isFalse();
            assertThat(serverFailure.get()).isNull();
        }
    }

    private static void sendSspiAuthenticationRequest(ServerSocket server,
            AtomicReference<Throwable> serverFailure) {
        try (Socket socket = server.accept();
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            int startupLength = input.readInt();
            byte[] startupPacket = input.readNBytes(startupLength - Integer.BYTES);
            if (startupPacket.length != startupLength - Integer.BYTES) {
                throw new IOException("Incomplete PostgreSQL startup packet");
            }

            output.writeByte('R');
            output.writeInt(8);
            output.writeInt(9);
            output.flush();
        } catch (Throwable failure) {
            serverFailure.set(failure);
        }
    }
}
