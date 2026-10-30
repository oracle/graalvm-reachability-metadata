/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import oracle.jdbc.oracore.Util;
import oracle.sql.NUMBER;
import org.junit.jupiter.api.Test;

public class UtilTest {
    @Test
    void serializesOracleNumericValuesWithoutLosingPrecision() throws Exception {
        BigDecimal amount = new BigDecimal("12345678901234567890.125");
        NUMBER original = new NUMBER(amount);

        byte[] encoded = Util.serializeObject(original);
        NUMBER restored = (NUMBER) Util.deserializeObject(encoded);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.bigDecimalValue()).isEqualByComparingTo(amount);
        assertThat(Util.serializeObject(null)).isNull();
        assertThat(Util.deserializeObject(null)).isNull();
    }
}
