/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.Properties;
import oracle.jdbc.internal.OracleConnection;
import oracle.jdbc.pool.OraclePooledConnection;
import org.junit.jupiter.api.Test;

public class OraclePooledConnectionTest {
    @Test
    void persistsConnectionPropertiesForPoolReconnection() throws Exception {
        PooledConnectionHandler handler = new PooledConnectionHandler();
        OracleConnection physicalConnection = (OracleConnection) Proxy.newProxyInstance(
                OracleConnection.class.getClassLoader(),
                new Class<?>[] {OracleConnection.class},
                handler);
        OraclePooledConnection original = new OraclePooledConnection(physicalConnection);

        OraclePooledConnection restored = roundTrip(original);

        assertThat(handler.isPropertiesRequested()).isTrue();
        assertThat(handler.isClosed()).isTrue();
        assertThat(restored).isNotNull();
        assertThat(restored.getPhysicalHandle()).isNull();
    }

    private static OraclePooledConnection roundTrip(OraclePooledConnection pooledConnection)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(pooledConnection);
        }
        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (OraclePooledConnection) input.readObject();
        }
    }

    private static final class PooledConnectionHandler implements InvocationHandler {
        private boolean propertiesRequested;
        private boolean closed;

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getName().equals("getPropertyForPooledConnection")) {
                Properties connectionProperties = new Properties();
                connectionProperties.setProperty(
                        OraclePooledConnection.url_string,
                        "jdbc:oracle:thin:@//127.0.0.1:1/test-service");
                connectionProperties.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
                connectionProperties.setProperty("oracle.jdbc.ReadTimeout", "10000");
                Hashtable<Object, Object> pooledProperties = new Properties();
                pooledProperties.put(
                        OraclePooledConnection.connection_properties_string, connectionProperties);
                ((OraclePooledConnection) arguments[0]).setProperties(pooledProperties);
                propertiesRequested = true;
                return null;
            }
            if (method.getName().equals("close")) {
                closed = true;
                return null;
            }
            return defaultValue(method.getReturnType());
        }

        private static Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive() || returnType == void.class) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == char.class) {
                return '\0';
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0.0F;
            }
            return 0.0D;
        }

        boolean isPropertiesRequested() {
            return propertiesRequested;
        }

        boolean isClosed() {
            return closed;
        }
    }
}
