/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.oracore.OracleTypeUPT;
import oracle.jdbc.oracore.Util;
import org.junit.jupiter.api.Test;

public class OracleTypeUPTTest {
    @Test
    void transportsAnUnresolvedUserTypeWithoutAConnection() throws Exception {
        OracleTypeUPT type = new OracleTypeUPT("APP.CONTACT", null);
        type.setNames("APP", "CONTACT");

        OracleTypeUPT restored = (OracleTypeUPT) Util.deserializeObject(Util.serializeObject(type));

        assertThat(restored).isNotSameAs(type);
        assertThat(restored.getFullName()).isEqualTo("APP.CONTACT");
        assertThat(restored.getRealType()).isNull();
        assertThat(restored.isObjectType()).isFalse();
    }
}
