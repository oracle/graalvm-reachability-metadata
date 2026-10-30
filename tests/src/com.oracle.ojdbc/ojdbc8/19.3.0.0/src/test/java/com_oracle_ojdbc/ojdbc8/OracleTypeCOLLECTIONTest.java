/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import oracle.jdbc.oracore.OracleTypeCOLLECTION;
import oracle.jdbc.oracore.Util;
import org.junit.jupiter.api.Test;

public class OracleTypeCOLLECTIONTest {
    @Test
    void transportsTheNameOfAnUnresolvedCollectionType() throws Exception {
        OracleTypeCOLLECTION type = new OracleTypeCOLLECTION("APP.ADDRESSES", null);
        type.setNames("APP", "ADDRESSES");

        OracleTypeCOLLECTION restored = (OracleTypeCOLLECTION) Util.deserializeObject(Util.serializeObject(type));

        assertThat(restored).isNotSameAs(type);
        assertThat(restored.getFullName()).isEqualTo("APP.ADDRESSES");
        assertThat(restored.getTypeCode()).isEqualTo(Types.ARRAY);
        assertThat(restored.getElementType()).isNull();
        assertThat(restored.getMaxLength()).isZero();
    }
}
