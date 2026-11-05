/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import oracle.jdbc.OracleTypes;
import oracle.jdbc.driver.OracleStruct;
import org.junit.jupiter.api.Test;

public class OracleStructTest {
    @Test
    void mapsAnObjectImageThroughItsRegisteredFactory() throws Exception {
        OfflineConnection connection = new OfflineConnection();
        OracleStruct value = new OracleStruct(connection.structDescriptor(), new byte[] {7, 11}, connection);

        ApplicationValue mapped = (ApplicationValue) value.toJdbc(Map.of("APP.CONTACT", ApplicationValue.class));

        assertThat(mapped.toJDBCObject(connection)).isSameAs(value);
        assertThat(mapped.sqlType()).isEqualTo(OracleTypes.STRUCT);
        assertThat(mapped.bytes()).containsExactly(7, 11);
        assertThat(value.getSQLTypeName()).isEqualTo("APP.CONTACT");
    }
}
