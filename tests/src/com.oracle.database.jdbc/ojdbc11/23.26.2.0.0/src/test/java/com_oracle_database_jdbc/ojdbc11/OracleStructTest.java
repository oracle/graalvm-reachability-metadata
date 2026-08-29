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
import oracle.jdbc.driver.OracleConnection;
import oracle.jdbc.driver.OracleStruct;
import oracle.jdbc.oracore.OracleTypeADT;
import oracle.sql.StructDescriptor;
import org.junit.jupiter.api.Test;

public class OracleStructTest {
    private static final String TYPE_NAME = "APP.TEST_OBJECT";

    @Test
    void convertsToAnOracleDataFactory() throws Exception {
        TestConnection connection = new TestConnection();
        OracleTypeADT pickler = new OracleTypeADT(new byte[] {1, 2, 3}, 3, 873, (short) 1, TYPE_NAME);
        StructDescriptor descriptor = new StructDescriptor(pickler, connection);
        OracleStruct struct = new OracleStruct(descriptor, new byte[0], connection);

        RecordingFactory converted = (RecordingFactory) struct.toClass(RecordingFactory.class, Map.of());

        assertThat(converted.toJDBCObject(null)).isSameAs(struct);
        assertThat(converted.getSqlType()).isEqualTo(OracleTypes.STRUCT);
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
