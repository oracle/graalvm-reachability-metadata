/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.deps.org.apache.velocity.runtime.parser.node.MathUtils;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

public class MathUtilsTest {
    @Test
    void exercisesPublicApi() throws Exception {
        assertEquals(5, MathUtils.add(2, 3));
        assertEquals(new BigInteger("9223372036854775808"), MathUtils.add(Long.MAX_VALUE, 1L));
        assertEquals(new BigDecimal("2.5"), MathUtils.divide(new BigDecimal("5.0"), new BigDecimal("2")));
    }
}
