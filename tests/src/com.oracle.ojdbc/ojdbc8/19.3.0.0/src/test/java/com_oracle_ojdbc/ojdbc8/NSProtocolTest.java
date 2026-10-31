/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import oracle.jdbc.OracleDriver;
import oracle.net.ns.NetException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(OracleDatabase.class)
public class NSProtocolTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(120)
    void negotiatesNetworkSecurityOverAnEncryptedTransport() throws Exception {
        Path trustStore = directory.resolve("tcps.p12");
        try (var resource = NSProtocolTest.class.getResourceAsStream("/tcps.p12")) {
            Files.copy(resource, trustStore);
        }
        try (TlsTunnel tunnel = new TlsTunnel(trustStore, OracleDatabase.url())) {
            Properties options = OracleDatabase.properties();
            options.setProperty("oracle.jdbc.javaNetNio", "false");
            options.setProperty("oracle.net.encryption_client", "REJECTED");
            options.setProperty("javax.net.ssl.trustStore", trustStore.toString());
            options.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
            options.setProperty("javax.net.ssl.trustStorePassword", TlsTunnel.PASSWORD);
            options.setProperty("oracle.net.ssl_version", "1.2");
            String url = "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)(HOST=127.0.0.1)(PORT="
                    + tunnel.port() + "))(CONNECT_DATA=(SERVICE_NAME=" + tunnel.service() + ")))";

            try (Connection connection = new OracleDriver().connect(url, options);
                    Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT 'encrypted connection' FROM dual")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("encrypted connection");
                assertThat(result.next()).isFalse();
            }
            assertThat(tunnel.cipherSuite()).startsWith("TLS_").doesNotContain("NULL");
        }
    }

    @Test
    @Timeout(60)
    void initializesNetworkSecurityAndReportsAListenerServiceRefusal() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            listener.setSoTimeout(10000);
            Future<String> request = executor.submit(() -> refuseUnknownService(listener));
            Properties options = new Properties();
            options.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
            options.setProperty("oracle.jdbc.ReadTimeout", "10000");
            options.setProperty("oracle.jdbc.javaNetNio", "true");
            String url = "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=127.0.0.1)(PORT="
                    + listener.getLocalPort() + "))(CONNECT_DATA=(SERVICE_NAME=not_registered)))";

            SQLException error = assertThrows(SQLException.class, () -> new OracleDriver().connect(url, options));

            assertThat(error.getMessage()).contains("12514");
            assertThat((Throwable) error).hasRootCauseInstanceOf(NetException.class);
            assertThat(request.get(20, TimeUnit.SECONDS)).contains("SERVICE_NAME=not_registered");
        }
    }

    private static String refuseUnknownService(ServerSocket listener) throws Exception {
        try (Socket socket = listener.accept()) {
            socket.setSoTimeout(10000);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            int length = input.readUnsignedShort();
            byte[] connect = input.readNBytes(length - 2);
            assertThat(connect).hasSize(length - 2);
            assertThat(connect[2]).isEqualTo((byte) 1);

            byte[] reason = "(DESCRIPTION=(ERROR_STACK=(ERROR=(CODE=12514)(EMFI=4))))"
                    .getBytes(StandardCharsets.US_ASCII);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            // Oracle Net REFUSE: eight-byte header, two reason bytes, then a length-prefixed diagnostic.
            output.writeShort(12 + reason.length);
            output.writeShort(0);
            output.writeByte(4);
            output.writeByte(0);
            output.writeShort(0);
            output.writeByte(1);
            output.writeByte(1);
            output.writeShort(reason.length);
            output.write(reason);
            output.flush();
            return new String(connect, StandardCharsets.US_ASCII);
        }
    }
}
