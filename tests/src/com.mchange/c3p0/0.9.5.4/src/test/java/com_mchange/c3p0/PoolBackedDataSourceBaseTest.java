/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.PoolBackedDataSource;
import org.junit.jupiter.api.Test;

import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class PoolBackedDataSourceBaseTest {
    @Test
    void exposesConfiguredPoolBackedProperties() throws Exception {
        PoolBackedDataSource dataSource = C3p0TestSupport.newPoolBackedDataSource("pool-base", false, 0);
        Map<String, String> extensions = new HashMap<>();
        extensions.put("role", "test");
        dataSource.setExtensions(extensions);
        dataSource.setFactoryClassLocation("factory-location");
        dataSource.setNumHelperThreads(2);

        try {
            assertThat(dataSource.getDataSourceName()).isEqualTo("pool-base");
            assertThat(dataSource.getExtensions()).containsEntry("role", "test");
            assertThat(dataSource.getNumHelperThreads()).isEqualTo(2);
        } finally {
            dataSource.close();
        }
    }

    @Test
    void roundTripsSerializableConnectionPoolDataSourceAndBaseProperties() throws Exception {
        PoolBackedDataSource dataSource = C3p0TestSupport.newPoolBackedDataSource("pool-base-direct", false, 0);
        Map<String, String> extensions = new HashMap<>();
        extensions.put("role", "test");
        extensions.put("mode", "direct");
        dataSource.setExtensions(extensions);
        dataSource.setFactoryClassLocation("factory-location");
        dataSource.setIdentityToken("pool-base-direct-token");
        dataSource.setNumHelperThreads(2);

        PoolBackedDataSource restored = null;
        try {
            restored = C3p0TestSupport.roundTrip(dataSource);

            assertThat(restored.getConnectionPoolDataSource()).isNotNull();
            assertThat(restored.getDataSourceName()).isEqualTo("pool-base-direct");
            assertThat(restored.getExtensions())
                .containsEntry("role", "test")
                .containsEntry("mode", "direct");
            assertThat(restored.getFactoryClassLocation()).isEqualTo("factory-location");
            assertThat(restored.getIdentityToken()).isEqualTo("pool-base-direct-token");
            assertThat(restored.getNumHelperThreads()).isEqualTo(2);
        } finally {
            if (restored != null) {
                restored.close();
            }
            dataSource.close();
        }
    }

    @Test
    void roundTripsReferenceableConnectionPoolDataSourceThroughIndirectSerialization() throws Exception {
        PoolBackedDataSource dataSource = new PoolBackedDataSource(false);
        dataSource.setConnectionPoolDataSource(new NonSerializableReferenceableConnectionPoolDataSource("indirect-cpds"));
        dataSource.setDataSourceName("pool-base-indirect");
        Map<String, String> extensions = new HashMap<>();
        extensions.put("role", "test");
        extensions.put("mode", "indirect");
        dataSource.setExtensions(extensions);
        dataSource.setFactoryClassLocation("factory-location");
        dataSource.setIdentityToken("pool-base-indirect-token");
        dataSource.setNumHelperThreads(3);

        PoolBackedDataSource restored = null;
        try {
            restored = C3p0TestSupport.roundTrip(dataSource);

            assertThat(restored.getConnectionPoolDataSource())
                .isInstanceOf(NonSerializableReferenceableConnectionPoolDataSource.class);
            NonSerializableReferenceableConnectionPoolDataSource restoredConnectionPoolDataSource =
                (NonSerializableReferenceableConnectionPoolDataSource) restored.getConnectionPoolDataSource();
            assertThat(restoredConnectionPoolDataSource.getName()).isEqualTo("indirect-cpds");
            assertThat(restored.getDataSourceName()).isEqualTo("pool-base-indirect");
            assertThat(restored.getExtensions())
                .containsEntry("role", "test")
                .containsEntry("mode", "indirect");
            assertThat(restored.getFactoryClassLocation()).isEqualTo("factory-location");
            assertThat(restored.getIdentityToken()).isEqualTo("pool-base-indirect-token");
            assertThat(restored.getNumHelperThreads()).isEqualTo(3);
        } finally {
            if (restored != null) {
                restored.close();
            }
            dataSource.close();
        }
    }

    @Test
    void serializesReferenceableExtensionsThroughIndirectSerialization() throws Exception {
        PoolBackedDataSource dataSource = C3p0TestSupport.newPoolBackedDataSource("pool-base-extensions", false, 0);
        Map<String, String> extensions = new HashMap<>();
        extensions.put("marker", "replace-me");
        dataSource.setExtensions(extensions);
        dataSource.setFactoryClassLocation("factory-location");
        dataSource.setIdentityToken("pool-base-extensions-token");
        dataSource.setNumHelperThreads(4);

        PoolBackedDataSource restored = null;
        try {
            byte[] serialized = serialize(dataSource);
            restored = deserializeWithReferenceableExtensions(serialized);

            assertThat(restored.getExtensions())
                .isInstanceOf(NonSerializableReferenceableMap.class)
                .containsEntry("role", "test")
                .containsEntry("mode", "indirect-extensions");
            assertThat(serialize(restored)).isNotEmpty();
        } finally {
            if (restored != null) {
                restored.close();
            }
            dataSource.close();
        }
    }

    private static final class NonSerializableReferenceableConnectionPoolDataSource
        implements ConnectionPoolDataSource, Referenceable {
        private final String name;
        private PrintWriter logWriter;
        private int loginTimeout;

        private NonSerializableReferenceableConnectionPoolDataSource(String name) {
            this.name = name;
        }

        private String getName() {
            return name;
        }

        @Override
        public Reference getReference() {
            Reference reference = new Reference(
                NonSerializableReferenceableConnectionPoolDataSource.class.getName(),
                NonSerializableReferenceableConnectionPoolDataSourceFactory.class.getName(),
                null
            );
            reference.add(new StringRefAddr("name", name));
            return reference;
        }

        @Override
        public PooledConnection getPooledConnection() throws SQLException {
            throw new SQLFeatureNotSupportedException("Pooled connections are not required for this test.");
        }

        @Override
        public PooledConnection getPooledConnection(String user, String password) throws SQLException {
            throw new SQLFeatureNotSupportedException("Pooled connections are not required for this test.");
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            this.logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            this.loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("Parent logger is not supported.");
        }

    }

    private static byte[] serialize(Serializable value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static PoolBackedDataSource deserializeWithReferenceableExtensions(byte[] serialized) throws Exception {
        try (ReplacingObjectInputStream input = new ReplacingObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (PoolBackedDataSource) input.readObject();
        }
    }

    private static final class ReplacingObjectInputStream extends ObjectInputStream {
        private ReplacingObjectInputStream(InputStream input) throws IOException {
            super(input);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object obj) {
            if (
                obj instanceof Map<?, ?> map
                    && obj.getClass().getName().startsWith("java.util.Collections$")
                    && "replace-me".equals(map.get("marker"))
            ) {
                return new NonSerializableReferenceableMap(Map.of("role", "test", "mode", "indirect-extensions"));
            }
            return obj;
        }
    }

    private static final class NonSerializableReferenceableMap extends AbstractMap<String, String> implements Referenceable {
        private final Map<String, String> delegate;

        private NonSerializableReferenceableMap(Map<String, String> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            return delegate.entrySet();
        }

        @Override
        public Reference getReference() {
            return new Reference(NonSerializableReferenceableMap.class.getName(), null, null);
        }
    }

    public static final class NonSerializableReferenceableConnectionPoolDataSourceFactory implements ObjectFactory {
        @Override
        public Object getObjectInstance(
            Object obj,
            javax.naming.Name name,
            javax.naming.Context nameCtx,
            Hashtable<?, ?> environment
        ) {
            Reference reference = (Reference) obj;
            return new NonSerializableReferenceableConnectionPoolDataSource(
                (String) reference.get("name").getContent()
            );
        }
    }
}
