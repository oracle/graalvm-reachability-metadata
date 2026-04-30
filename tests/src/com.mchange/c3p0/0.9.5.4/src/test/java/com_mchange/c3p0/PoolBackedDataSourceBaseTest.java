/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.impl.PoolBackedDataSourceBase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import org.junit.jupiter.api.Test;

public class PoolBackedDataSourceBaseTest {
    @Test
    void serializesAndDeserializesConfiguredDataSourceBase() throws Exception {
        PoolBackedDataSourceBase source = configuredBase(new SerializableConnectionPoolDataSource("direct-pool"));

        PoolBackedDataSourceBase restored = roundTrip(source);

        assertThat(restored.getConnectionPoolDataSource()).isInstanceOf(SerializableConnectionPoolDataSource.class);
        SerializableConnectionPoolDataSource restoredPool =
                (SerializableConnectionPoolDataSource) restored.getConnectionPoolDataSource();
        assertThat(restoredPool.name).isEqualTo("direct-pool");
        assertThat(restored.getDataSourceName()).isEqualTo("pool-backed-data-source");
        assertThat(restored.getExtensions()).containsEntry("tenant", "integration-test");
        assertThat(restored.getFactoryClassLocation()).isEqualTo("factory/location");
        assertThat(restored.getIdentityToken()).isEqualTo("pool-token");
        assertThat(restored.getNumHelperThreads()).isEqualTo(5);
    }

    @Test
    void serializesReferenceableConnectionPoolDataSourceIndirectly() throws Exception {
        PoolBackedDataSourceBase source = configuredBase(new NonSerializableReferenceableConnectionPoolDataSource());

        byte[] serialized = serialize(source);

        assertThat(serialized).isNotEmpty();
    }

    private static PoolBackedDataSourceBase configuredBase(
            ConnectionPoolDataSource connectionPoolDataSource) throws Exception {
        PoolBackedDataSourceBase source = new PoolBackedDataSourceBase(false);
        Map<String, String> extensions = new LinkedHashMap<>();
        extensions.put("tenant", "integration-test");
        source.setConnectionPoolDataSource(connectionPoolDataSource);
        source.setDataSourceName("pool-backed-data-source");
        source.setExtensions(extensions);
        source.setFactoryClassLocation("factory/location");
        source.setIdentityToken("pool-token");
        source.setNumHelperThreads(5);
        return source;
    }

    private static PoolBackedDataSourceBase roundTrip(PoolBackedDataSourceBase source) throws Exception {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(source)))) {
            return (PoolBackedDataSourceBase) inputStream.readObject();
        }
    }

    private static byte[] serialize(PoolBackedDataSourceBase source) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteStream)) {
            outputStream.writeObject(source);
        }
        return byteStream.toByteArray();
    }

    private static final class SerializableConnectionPoolDataSource implements ConnectionPoolDataSource, Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;

        private SerializableConnectionPoolDataSource(String name) {
            this.name = name;
        }

        @Override
        public PooledConnection getPooledConnection() throws SQLException {
            throw new SQLException("Connection access is not required for serialization coverage.");
        }

        @Override
        public PooledConnection getPooledConnection(String user, String password) throws SQLException {
            throw new SQLException("Connection access is not required for serialization coverage.");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    private static final class NonSerializableReferenceableConnectionPoolDataSource
            implements ConnectionPoolDataSource, Referenceable {
        @Override
        public Reference getReference() {
            Reference reference = new Reference(NonSerializableReferenceableConnectionPoolDataSource.class.getName());
            reference.add(new StringRefAddr("name", "indirect-pool"));
            return reference;
        }

        @Override
        public PooledConnection getPooledConnection() throws SQLException {
            throw new SQLException("Connection access is not required for serialization coverage.");
        }

        @Override
        public PooledConnection getPooledConnection(String user, String password) throws SQLException {
            throw new SQLException("Connection access is not required for serialization coverage.");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }
}
