/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.velocity.runtime.parser.node.MathUtils;
import org.junit.jupiter.api.Test;

public class MathUtilsTest {
    @Test
    void performsNumericOperationsAcrossPrimitiveAndBigNumberTypes() {
        assertThat(MathUtils.isInteger(Integer.valueOf(1))).isTrue();
        assertThat(MathUtils.isInteger(Float.valueOf(1.0f))).isFalse();

        assertThat(MathUtils.add(Byte.valueOf((byte) 120), Byte.valueOf((byte) 7)))
                .isEqualTo(Byte.valueOf((byte) 127));
        assertThat(MathUtils.add(Byte.valueOf((byte) 120), Byte.valueOf((byte) 8)))
                .isEqualTo(Short.valueOf((short) 128));
        assertThat(MathUtils.subtract(Short.valueOf((short) -32768), Byte.valueOf((byte) 1)))
                .isEqualTo(Integer.valueOf(-32769));
        assertThat(MathUtils.multiply(Integer.valueOf(2), Long.valueOf(3)))
                .isEqualTo(Long.valueOf(6));

        assertThat(MathUtils.divide(Double.valueOf(7.5d), Integer.valueOf(3)))
                .isEqualTo(Double.valueOf(2.5d));
        assertThat(MathUtils.modulo(Float.valueOf(7.0f), Integer.valueOf(4)))
                .isEqualTo(Float.valueOf(3.0f));

        assertThat(MathUtils.add(BigInteger.valueOf(Long.MAX_VALUE), BigInteger.ONE))
                .isEqualTo(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        BigDecimal decimalProduct = (BigDecimal) MathUtils.multiply(new BigDecimal("1.50"), Integer.valueOf(2));
        assertThat(decimalProduct).isEqualByComparingTo(new BigDecimal("3.00"));
    }

    @Test
    void comparesAndDetectsZeroForAllSupportedNumberFamilies() {
        assertThat(MathUtils.isZero(BigInteger.ZERO)).isTrue();
        assertThat(MathUtils.isZero(Float.valueOf(0.0f))).isTrue();
        assertThat(MathUtils.isZero(Double.valueOf(0.0d))).isTrue();
        assertThat(MathUtils.isZero(new BigDecimal("0.00"))).isTrue();
        assertThat(MathUtils.isZero(Integer.valueOf(1))).isFalse();

        assertThat(MathUtils.compare(Integer.valueOf(3), Long.valueOf(2))).isPositive();
        assertThat(MathUtils.compare(Float.valueOf(2.5f), Double.valueOf(2.5d))).isZero();
        assertThat(MathUtils.compare(new BigDecimal("2.50"), BigInteger.valueOf(3))).isNegative();
    }
}
