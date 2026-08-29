/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import oracle.jdbc.OracleData;
import oracle.jdbc.OracleDataFactory;
import oracle.jdbc.OracleTypes;
import oracle.jdbc.TraceEventListener;
import oracle.jdbc.driver.OracleArray;
import oracle.jdbc.driver.OracleConnection;
import oracle.jdbc.oracore.OracleTypeCOLLECTION;
import oracle.sql.ArrayDescriptor;
import org.junit.jupiter.api.Test;

public class OracleArrayTest {
    private static final String TYPE_NAME = "APP.TEST_COLLECTION";

    @Test
    void convertsToAnOracleDataFactory() throws Exception {
        TestConnection connection = new TestConnection();
        ArrayDescriptor descriptor =
                new ArrayDescriptor(new OracleTypeCOLLECTION(TYPE_NAME, connection), connection);
        OracleArray array = new OracleArray(descriptor, new byte[0], connection);

        RecordingFactory converted =
                (RecordingFactory) array.toJdbc(Map.of(TYPE_NAME, RecordingFactory.class));

        assertThat(converted.toJDBCObject(null)).isSameAs(array);
        assertThat(converted.getSqlType()).isEqualTo(OracleTypes.ARRAY);
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
        public Object toJDBCObject(Connection connection) {
            return value;
        }

        public int getSqlType() {
            return sqlType;
        }
    }

    private static final class TestConnection extends OracleConnection {
        private final Map<Object, Object> clientData = new HashMap<>();
        private Map<String, Class<?>> typeMap = new HashMap<>();

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
        public TraceEventListener getTraceEventListener() {
            return null;
        }

        @Override
        public TestConnection physicalConnectionWithin() {
            return this;
        }

        @Override
        public boolean isDescriptorSharable(oracle.jdbc.internal.OracleConnection connection) {
            return connection == this;
        }

        @Override
        public Map<String, Class<?>> getTypeMap() {
            return typeMap;
        }

        @Override
        public void setTypeMap(Map<String, Class<?>> typeMap) {
            this.typeMap = typeMap;
        }
    }
}
