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
import oracle.jdbc.driver.OracleRef;
import org.junit.jupiter.api.Test;

public class OracleRefTest {
    @Test
    void mapsAReferenceWithoutDereferencingIt() throws Exception {
        OracleRef reference = new OracleRef("APP.CONTACT", new OfflineConnection(), new byte[] {4, 8, 12});
        ApplicationValue mapped = (ApplicationValue) reference.toClass(ApplicationValue.class, Map.of());

        assertThat(mapped.toJDBCObject(null)).isSameAs(reference);
        assertThat(mapped.sqlType()).isEqualTo(OracleTypes.REF);
        assertThat(mapped.bytes()).containsExactly(4, 8, 12);

        assertThat(reference.getBaseTypeName()).isEqualTo("APP.CONTACT");
    }
}
