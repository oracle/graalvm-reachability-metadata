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
import java.sql.Connection;
import java.util.Map;
import oracle.jdbc.OracleConnectionWrapper;
import oracle.jdbc.OracleData;
import oracle.jdbc.OracleDataFactory;
import oracle.jdbc.OracleTypes;
import oracle.jdbc.internal.OracleConnection;
import oracle.sql.REF;
import org.junit.jupiter.api.Test;

public class REFTest {
    @Test
    void convertsToAnOracleDataFactory() throws Exception {
        REF ref = new REF("APP.TEST_REF", new DetachedConnection(), new byte[] {1, 2, 3});

        RecordingFactory converted = (RecordingFactory) ref.toClass(RecordingFactory.class, Map.of());

        assertThat(converted.toJDBCObject(null)).isSameAs(ref);
        assertThat(converted.getSqlType()).isEqualTo(OracleTypes.REF);
    }

    @Test
    void roundTripsReferenceIdentityThroughSerialization() throws Exception {
        REF ref = new REF("APP.TEST_REF", new DetachedConnection(), new byte[] {2, 4, 6});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(ref);
        }

        REF restored;
        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (REF) input.readObject();
        }

        assertThat(restored.getBaseTypeName()).isEqualTo("APP.TEST_REF");
        assertThat(restored.shareBytes()).containsExactly(2, 4, 6);
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

    private static final class DetachedConnection extends OracleConnectionWrapper {
        @Override
        public OracleConnection physicalConnectionWithin() {
            return null;
        }
    }
}
