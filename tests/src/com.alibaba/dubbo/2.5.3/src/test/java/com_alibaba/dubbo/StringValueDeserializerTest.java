/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

public class StringValueDeserializerTest {
    @Test
    void restoresStringConstructedValues() throws Exception {
        BigDecimal source = new BigDecimal("1234567890.125");

        assertThat(HessianTestSupport.roundTrip(source)).isEqualTo(source);
    }
}
