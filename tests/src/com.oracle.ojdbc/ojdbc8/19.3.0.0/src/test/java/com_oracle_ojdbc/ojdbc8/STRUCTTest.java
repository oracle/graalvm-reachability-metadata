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
import oracle.sql.STRUCT;
import org.junit.jupiter.api.Test;

public class STRUCTTest {
    @Test
    void convertsAJdbcStructUsingItsSqlTypeMap() throws Exception {
        OfflineConnection connection = new OfflineConnection();
        STRUCT value = new STRUCT(connection.structDescriptor(), new byte[] {9, 13}, connection);

        ApplicationValue mapped = (ApplicationValue) value.toJdbc(Map.of("APP.CONTACT", ApplicationValue.class));

        assertThat(mapped.toJDBCObject(connection)).isSameAs(value);
        assertThat(mapped.sqlType()).isEqualTo(OracleTypes.STRUCT);
        assertThat(mapped.bytes()).containsExactly(9, 13);
        assertThat(value.getSQLTypeName()).isEqualTo("APP.CONTACT");
    }
}
