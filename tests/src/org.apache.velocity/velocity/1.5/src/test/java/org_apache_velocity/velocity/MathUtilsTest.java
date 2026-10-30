/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MathUtilsTest {
    @Test
    void evaluatesArithmeticAndPromotesOverflow() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("maximum", Long.MAX_VALUE);
        context.put("price", new BigDecimal("12.50"));
        assertEquals("9223372036854775808|25.00|3|1", VelocityTestSupport.render(context,
                "#set($large = $maximum + 1)#set($total = $price * 2)"
                        + "#set($quotient = 10 / 3)#set($remainder = 10 % 3)"
                        + "$large|$total|$quotient|$remainder"));
        assertEquals(new BigInteger("9223372036854775808"), context.get("large"));
        assertEquals(new BigDecimal("25.00"), context.get("total"));
    }
}
