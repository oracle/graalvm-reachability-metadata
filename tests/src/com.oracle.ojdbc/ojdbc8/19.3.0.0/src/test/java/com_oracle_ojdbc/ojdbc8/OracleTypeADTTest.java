/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.oracore.OracleTypeADT;
import oracle.jdbc.oracore.Util;
import oracle.sql.CharacterSet;
import org.junit.jupiter.api.Test;

public class OracleTypeADTTest {
    @Test
    void transportsNamedObjectTypeIdentityThroughTheDriverSerializer() throws Exception {
        byte[] identifier = {8, 6, 7, 5, 3, 0, 9};
        OracleTypeADT type = new OracleTypeADT(identifier, 2,
                CharacterSet.AL32UTF8_CHARSET, (short) 1, "APP.ADDRESS");
        type.setNames("APP", "ADDRESS");

        byte[] image = Util.serializeObject(type);
        OracleTypeADT restored = (OracleTypeADT) Util.deserializeObject(image);

        assertThat(restored).isNotSameAs(type);
        assertThat(restored.getFullName()).isEqualTo("APP.ADDRESS");
        assertThat(restored.getTOID()).containsExactly(identifier);
        assertThat(restored.getTypeVersion()).isEqualTo(2);
        assertThat(restored.getCharSet()).isEqualTo(CharacterSet.AL32UTF8_CHARSET);
        assertThat(restored.getCharSetForm()).isEqualTo(1);
    }
}
