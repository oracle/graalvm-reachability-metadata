/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KryoInnerDefaultInstantiatorStrategyTest {
    @Test
    void reportsNonStaticMemberClassesWithoutUsingFallbackStrategy() {
        Kryo.DefaultInstantiatorStrategy strategy = new Kryo.DefaultInstantiatorStrategy();

        assertThatThrownBy(() -> strategy.newInstantiatorOf(Outer.NonStaticMember.class))
                .isInstanceOf(KryoException.class)
                .hasMessageContaining("non-static member class")
                .hasMessageContaining("NonStaticMember");
    }

    private static class Outer {
        class NonStaticMember {
        }
    }
}
