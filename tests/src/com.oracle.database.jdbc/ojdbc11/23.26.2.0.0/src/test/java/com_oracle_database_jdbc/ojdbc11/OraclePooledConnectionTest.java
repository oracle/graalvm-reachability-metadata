/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import oracle.jdbc.driver.OracleConnection;
import oracle.jdbc.pool.OraclePooledConnection;
import org.junit.jupiter.api.Test;

public class OraclePooledConnectionTest {
    @Test
    void serializesPooledConnectionProperties() throws Exception {
        OraclePooledConnection pooledConnection = new OraclePooledConnection(new TestConnection());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(pooledConnection);
        }

        assertThat(bytes.size()).isPositive();
    }

    private static final class TestConnection extends OracleConnection {
        private final Map<Object, Object> clientData = new HashMap<>();

        @Override
        public Object getClientData(Object key) {
            return clientData.get(key);
        }

        @Override
        public Object setClientData(Object key, Object value) {
            return clientData.put(key, value);
        }

        @Override
        public Object removeClientData(Object key) {
            return clientData.remove(key);
        }

        @Override
        public void setClientIdentifier(String clientIdentifier) { }

        @Override
        public void clearClientIdentifier(String clientIdentifier) { }

        @Override
        public void getPropertyForPooledConnection(OraclePooledConnection pooledConnection) {
            pooledConnection.setProperties(new Hashtable<>());
        }

        @Override
        public void close() { }
    }
}
