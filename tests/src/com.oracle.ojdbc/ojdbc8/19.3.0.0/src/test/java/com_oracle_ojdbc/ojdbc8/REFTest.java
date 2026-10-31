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
import oracle.sql.REF;
import org.junit.jupiter.api.Test;

public class REFTest {
    @Test
    void convertsAJdbcReferenceUsingAnApplicationFactory() throws Exception {
        REF reference = new REF("APP.CONTACT", new OfflineConnection(), new byte[] {2, 5, 8});
        ApplicationValue mapped = (ApplicationValue) reference.toClass(ApplicationValue.class, Map.of());

        assertThat(mapped.toJDBCObject(null)).isSameAs(reference);
        assertThat(mapped.sqlType()).isEqualTo(OracleTypes.REF);
        assertThat(mapped.bytes()).containsExactly(2, 5, 8);
        assertThat(reference.getBaseTypeName()).isEqualTo("APP.CONTACT");
    }
}
