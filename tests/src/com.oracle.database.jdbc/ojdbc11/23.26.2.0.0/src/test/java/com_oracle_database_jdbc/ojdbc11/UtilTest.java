/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.oracore.Util;
import org.junit.jupiter.api.Test;

public class UtilTest {
    @Test
    void serializesAndDeserializesObjects() throws Exception {
        byte[] serialized = Util.serializeObject("oracle-payload");

        assertThat(serialized).isNotEmpty();
        assertThat(Util.deserializeObject(serialized)).isEqualTo("oracle-payload");
    }
}
