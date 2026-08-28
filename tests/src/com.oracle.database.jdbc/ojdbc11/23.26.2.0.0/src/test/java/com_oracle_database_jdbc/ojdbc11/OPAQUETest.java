/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import oracle.jdbc.OracleData;
import oracle.jdbc.OracleDataFactory;
import oracle.jdbc.OracleTypes;
import oracle.sql.OPAQUE;
import oracle.sql.OpaqueDescriptor;
import org.junit.jupiter.api.Test;

public class OPAQUETest {
    @Test
    void obtainsStringValuesFromMappedJdbcObjects() throws Exception {
        OpaqueDescriptor descriptor = descriptor();
        OPAQUE getStringValue = new MappedOpaque(descriptor, new GetStringValue("alpha"));
        OPAQUE stringValue = new MappedOpaque(descriptor, new StringValue("beta"));

        assertThat(getStringValue.stringValue()).startsWith("OPAQUE").contains("alpha");
        assertThat(stringValue.stringValue()).startsWith("OPAQUE").contains("beta");
    }

    @Test
    void convertsToAnOracleDataFactory() throws Exception {
        OPAQUE opaque = new OPAQUE(descriptor(), null, new byte[] {2, 4, 6});

        RecordingFactory converted = (RecordingFactory) opaque.toClass(RecordingFactory.class, Map.of());

        assertThat(converted.toJDBCObject(null)).isSameAs(opaque);
        assertThat(converted.getSqlType()).isEqualTo(OracleTypes.OPAQUE);
    }

    private static OpaqueDescriptor descriptor() throws SQLException {
        return OpaqueDescriptor.createDescriptor("SYS.ANYTYPE", null);
    }

    public static final class GetStringValue {
        private final String value;

        public GetStringValue(String value) {
            this.value = value;
        }

        public String getStringVal() {
            return value;
        }
    }

    public static final class StringValue {
        private final String value;

        public StringValue(String value) {
            this.value = value;
        }

        public String stringValue() {
            return value;
        }
    }

    public static final class RecordingFactory implements OracleDataFactory, OracleData {
        private Object value;
        private int sqlType;

        public RecordingFactory() {}

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

    private static final class MappedOpaque extends OPAQUE {
        private final Object value;

        private MappedOpaque(OpaqueDescriptor descriptor, Object value) throws SQLException {
            super(descriptor, null, new byte[] {1});
            this.value = value;
        }

        @Override
        public Object toJdbc() {
            return value;
        }
    }
}
