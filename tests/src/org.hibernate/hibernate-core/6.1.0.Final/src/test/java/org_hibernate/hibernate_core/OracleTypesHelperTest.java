/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import oracle.jdbc.OracleTypes;
import org.hibernate.dialect.OracleTypesHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OracleTypesHelperTest {

    @Test
    public void readsTheOracleCursorJdbcType() {
        assertThat(OracleTypesHelper.INSTANCE.getOracleCursorTypeSqlType())
                .isEqualTo(OracleTypes.CURSOR);
    }
}
