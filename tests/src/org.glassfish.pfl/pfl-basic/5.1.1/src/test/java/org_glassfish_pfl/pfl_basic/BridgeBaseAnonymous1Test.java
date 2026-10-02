/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.pfl.basic.reflection.Bridge;
import org.junit.jupiter.api.Test;

public class BridgeBaseAnonymous1Test {
    @Test
    public void exposesUnsafeBackedFieldAccess() throws Exception {
        Bridge bridge = Bridge.get();
        Holder holder = new Holder();
        long offset = bridge.objectFieldOffset(Holder.class.getDeclaredField("value"));

        assertThat(bridge.getInt(holder, offset)).isEqualTo(4);
        bridge.putInt(holder, offset, 9);
        assertThat(holder.value).isEqualTo(9);
    }

    public static class Holder {
        private int value = 4;
    }
}
