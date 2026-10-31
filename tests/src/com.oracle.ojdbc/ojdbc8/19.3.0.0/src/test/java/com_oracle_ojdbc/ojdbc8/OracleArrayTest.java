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
import oracle.jdbc.driver.OracleArray;
import org.junit.jupiter.api.Test;

public class OracleArrayTest {
    @Test
    void mapsACollectionImageToAnApplicationValue() throws Exception {
        OfflineConnection connection = new OfflineConnection();
        OracleArray value = new OracleArray(connection.arrayDescriptor(), new byte[] {3, 6}, connection);

        ApplicationValue mapped = (ApplicationValue) value.toJdbc(Map.of("APP.CONTACTS", ApplicationValue.class));

        assertThat(mapped.toJDBCObject(connection)).isSameAs(value);
        assertThat(mapped.sqlType()).isEqualTo(OracleTypes.ARRAY);
        assertThat(mapped.bytes()).containsExactly(3, 6);
        assertThat(value.getSQLTypeName()).isEqualTo("APP.CONTACTS");
    }
}
