/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class EnumSerializerTest {
    @Test
    void writesEnumNames() throws Exception {
        assertThat(HessianTestSupport.roundTrip(State.READY)).isEqualTo(State.READY);
    }

    public enum State {
        READY,
        DONE
    }
}
