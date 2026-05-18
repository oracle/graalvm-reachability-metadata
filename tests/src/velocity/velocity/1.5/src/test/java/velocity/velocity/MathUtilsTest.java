/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.velocity.runtime.parser.node.MathUtils;
import org.junit.jupiter.api.Test;

public class MathUtilsTest {
    @Test
    public void performsArithmeticAcrossPrimitiveAndBigNumberTypes() {
        final Number byteValue = MathUtils.wrapPrimitive(7L, Byte.class);
        final Number shortValue = MathUtils.wrapPrimitive(300L, Short.class);
        final Number integerValue = MathUtils.add(byteValue, shortValue);
        final Number longValue = MathUtils.multiply(integerValue, Long.valueOf(2L));
        final Number decimalValue = MathUtils.divide(new BigDecimal("9.0"), Integer.valueOf(2));
        final Number bigIntegerValue = MathUtils.add(
                BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE);

        assertThat(byteValue).isEqualTo(Byte.valueOf((byte) 7));
        assertThat(shortValue).isEqualTo(Short.valueOf((short) 300));
        assertThat(integerValue).isEqualTo(Short.valueOf((short) 307));
        assertThat(longValue).isEqualTo(Long.valueOf(614L));
        assertThat((BigDecimal) decimalValue).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(bigIntegerValue).isEqualTo(new BigInteger("9223372036854775808"));
    }

    @Test
    public void comparesAndClassifiesNumericValues() {
        assertThat(MathUtils.isInteger(Integer.valueOf(10))).isTrue();
        assertThat(MathUtils.isInteger(Double.valueOf(10.0D))).isFalse();
        assertThat(MathUtils.isZero(BigDecimal.ZERO)).isTrue();
        assertThat(MathUtils.isZero(Float.valueOf(0.25F))).isFalse();
        assertThat(MathUtils.compare(BigInteger.TEN, Long.valueOf(9L))).isPositive();
        assertThat(MathUtils.modulo(Integer.valueOf(17), Byte.valueOf((byte) 5)))
                .isEqualTo(Integer.valueOf(2));
    }
}
