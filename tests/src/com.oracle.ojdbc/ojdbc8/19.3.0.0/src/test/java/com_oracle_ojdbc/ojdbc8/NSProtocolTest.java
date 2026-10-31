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
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import oracle.jdbc.OracleDriver;
import oracle.net.ns.NetException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class NSProtocolTest {
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
