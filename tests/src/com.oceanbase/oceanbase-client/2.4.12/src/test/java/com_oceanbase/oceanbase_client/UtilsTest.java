/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oceanbase.oceanbase_client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest extends DatabaseTestSupport {
    @Test
    void usesTheSocketFactorySelectedInConnectionProperties() throws Exception {
        int before = RecordingSocketFactory.creations.get();
        Properties properties = new Properties();
        properties.setProperty("socketFactory", RecordingSocketFactory.class.getName());
        try (Connection connection = database.connect("", properties);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 21 * 2")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(42);
            assertThat(result.next()).isFalse();
        }
        assertThat(RecordingSocketFactory.creations.get()).isGreaterThan(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"loadbalance", "replication", "aurora"})
    void executesQueriesThroughConfiguredHighAvailabilityProtocols(String mode) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("profileSql", "true");
        try (Connection connection = database.connect(mode, properties);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 'available', 42")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("available");
            assertThat(result.getInt(2)).isEqualTo(42);
            assertThat(result.next()).isFalse();
            assertThat(connection.isValid(10)).isTrue();
        }
    }

    public static class RecordingSocketFactory extends SocketFactory {
        private static final AtomicInteger creations = new AtomicInteger();

        public RecordingSocketFactory() {
        }

        @Override
        public Socket createSocket() {
            creations.incrementAndGet();
            return new Socket();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return new Socket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException {
            return new Socket(host, port, localAddress, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return new Socket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress host, int port, InetAddress localAddress, int localPort) throws IOException {
            return new Socket(host, port, localAddress, localPort);
        }
    }
}
