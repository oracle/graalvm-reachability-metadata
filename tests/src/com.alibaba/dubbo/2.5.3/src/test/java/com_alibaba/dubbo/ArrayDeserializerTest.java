/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ArrayDeserializerTest {
    @Test
    void restoresTypedArrays() throws Exception {
        Integer[] copy = (Integer[]) HessianTestSupport.roundTrip(new Integer[] {13, 21});

        assertThat(copy).containsExactly(13, 21);
    }
}
