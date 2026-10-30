/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

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
    void convertsOpaqueValuesUsingTheApplicationsFactory() throws Exception {
        OpaqueDescriptor descriptor = OpaqueDescriptor.createDescriptor("SYS.ANYTYPE", null);
        OPAQUE opaque = new OPAQUE(descriptor, null, new byte[] {10, 20, 30});

        Payload mapped = (Payload) opaque.toClass(Payload.class, Map.of());

        assertThat(mapped.toJDBCObject(null)).isSameAs(opaque);
        assertThat(mapped.sqlType).isEqualTo(OracleTypes.OPAQUE);
        assertThat(mapped.value.getBytesValue()).containsExactly(10, 20, 30);
    }

    @Test
    void rendersTheStringMethodsOfMappedApplicationValues() throws Exception {
        OpaqueDescriptor descriptor = OpaqueDescriptor.createDescriptor("SYS.ANYTYPE", null);
        OPAQUE primary = new ApplicationOpaque(descriptor, StringValPayload.class);
        OPAQUE fallback = new ApplicationOpaque(descriptor, StringValuePayload.class);

        assertThat(primary.stringValue()).isEqualTo("OPAQUE(primary text)");
        assertThat(fallback.stringValue()).isEqualTo("OPAQUE(fallback text)");
    }

    public static class Payload implements OracleData, OracleDataFactory {
        private OPAQUE value;
        private int sqlType;

        public Payload() {
        }

        @Override
        public OracleData create(Object source, int type) {
            value = (OPAQUE) source;
            sqlType = type;
            return this;
        }

        @Override
        public Object toJDBCObject(Connection connection) {
            return value;
        }
    }

    public static final class StringValPayload extends Payload {
        public StringValPayload() {
        }

        public String getStringVal() {
            return "primary text";
        }
    }

    public static final class StringValuePayload extends Payload {
        public StringValuePayload() {
        }

        public String stringValue() {
            return "fallback text";
        }
    }

    private static final class ApplicationOpaque extends OPAQUE {
        private final Class<?> factory;

        private ApplicationOpaque(OpaqueDescriptor descriptor, Class<?> factory) throws SQLException {
            super(descriptor, null, new byte[] {42});
            this.factory = factory;
        }

        @Override
        public Object toJdbc() throws SQLException {
            return toClass(factory, Map.of());
        }
    }
}
