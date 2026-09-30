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

public class BridgeBaseTest {
    @Test
    public void initializesClassThroughBridge() {
        Bridge bridge = Bridge.get();
        bridge.ensureClassInitialized(Initialized.class);

        assertThat(Initialized.initialized).isTrue();
    }

    public static class Initialized {
        static boolean initialized;

        static {
            initialized = true;
        }
    }
}
