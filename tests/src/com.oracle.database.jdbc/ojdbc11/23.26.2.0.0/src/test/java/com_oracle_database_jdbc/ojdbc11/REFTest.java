/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

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
