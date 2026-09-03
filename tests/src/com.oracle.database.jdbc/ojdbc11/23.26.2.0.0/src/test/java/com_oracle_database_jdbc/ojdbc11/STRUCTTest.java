/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Map;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleData;
import oracle.jdbc.OracleDataFactory;
import oracle.jdbc.OracleTypes;
import oracle.jdbc.oracore.OracleTypeADT;
import oracle.sql.SQLName;
import oracle.sql.STRUCT;
import oracle.sql.StructDescriptor;
import org.junit.jupiter.api.Test;

public class STRUCTTest {
    @Test
    void convertsMappedLegacyStructsToAnOracleDataFactory() throws Exception {
        OracleConnection connection = connection();
        STRUCT struct = new STRUCT(descriptor(connection), new byte[] {1}, connection);

        RecordingFactory converted =
                (RecordingFactory) struct.toClass(RecordingFactory.class, Map.of());

        assertThat(converted.toJDBCObject(null)).isSameAs(struct);
        assertThat(converted.getSqlType()).isEqualTo(OracleTypes.STRUCT);
    }

    private static StructDescriptor descriptor(OracleConnection connection) throws Exception {
        OracleTypeADT pickler =
                new OracleTypeADT(new byte[] {1, 2, 3}, 3, 873, (short) 1, "APP.TEST_OBJECT");
        SQLName name = new SQLName("APP", "TEST_OBJECT", null);
        return new DetachedStructDescriptor(name, pickler, connection);
    }

    private static final class DetachedStructDescriptor extends StructDescriptor {
        private DetachedStructDescriptor(
                SQLName name, OracleTypeADT pickler, OracleConnection connection)
                throws SQLException {
            super(name, pickler, connection);
        }

        @Override
        public void setConnection(java.sql.Connection connection) { }
    }

    private static OracleConnection connection() {
        return (OracleConnection) Proxy.newProxyInstance(
                OracleConnection.class.getClassLoader(),
                new Class<?>[] {oracle.jdbc.internal.OracleConnection.class},
                new ConnectionHandler());
    }

    private static final class ConnectionHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getName().equals("physicalConnectionWithin")
                    || method.getName().equals("getWrapper")) {
                return proxy;
            }
            if (method.getName().equals("isDescriptorSharable")) {
                return true;
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
    }

    public static final class RecordingFactory implements OracleDataFactory, OracleData {
        private Object value;
        private int sqlType;

        public RecordingFactory() { }

        @Override
        public OracleData create(Object value, int sqlType) {
            this.value = value;
            this.sqlType = sqlType;
            return this;
        }

        @Override
        public Object toJDBCObject(java.sql.Connection connection) {
            return value;
        }

        public int getSqlType() {
            return sqlType;
        }
    }
}
