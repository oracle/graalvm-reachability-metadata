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
import java.net.SocketTimeoutException;
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
public class SSPIClientTest {

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
            socket.setSoTimeout(15_000);
            int startupLength = input.readInt();
            byte[] startupPacket = input.readNBytes(startupLength - Integer.BYTES);
            if (startupPacket.length != startupLength - Integer.BYTES) {
                throw new IOException("Incomplete PostgreSQL startup packet");
            }

            output.writeByte('R');
            output.writeInt(8);
            output.writeInt(9);
            output.flush();

            int responseType = input.read();
            if (responseType < 0) {
                return;
            }
            if (responseType != 'p') {
                throw new IOException("Unexpected SSPI response type");
            }
            int responseLength = input.readInt();
            byte[] responseToken = input.readNBytes(responseLength - Integer.BYTES);
            if (responseToken.length != responseLength - Integer.BYTES) {
                throw new IOException("Incomplete SSPI response");
            }

            output.writeByte('R');
            output.writeInt(12);
            output.writeInt(8);
            output.writeInt(1);
            output.writeByte(0);
            output.flush();

            if (input.read() != 'p') {
                throw new IOException("Missing SSPI continuation response");
            }
        } catch (SocketTimeoutException ignored) {
            // SSPI is unavailable on non-Windows hosts, so the client may close after the first request.
        } catch (Throwable failure) {
            serverFailure.set(failure);
        }
    }
}
