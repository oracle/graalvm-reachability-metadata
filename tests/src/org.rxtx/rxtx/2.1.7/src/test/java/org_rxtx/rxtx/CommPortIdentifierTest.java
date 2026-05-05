/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rxtx.rxtx;

import static org.assertj.core.api.Assertions.assertThat;

import gnu.io.CommPortIdentifier;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

public class CommPortIdentifierTest {
    @Test
    void getPortIdentifiersInitializesRxtxDriver() {
        String serialPorts = System.getProperty("gnu.io.rxtx.SerialPorts");
        String parallelPorts = System.getProperty("gnu.io.rxtx.ParallelPorts");
        System.setProperty("gnu.io.rxtx.SerialPorts", "");
        System.setProperty("gnu.io.rxtx.ParallelPorts", "");

        try {
            Enumeration<?> portIdentifiers = CommPortIdentifier.getPortIdentifiers();

            assertThat(portIdentifiers).isNotNull();
            assertThat(portIdentifiers.hasMoreElements()).isFalse();
        } finally {
            restoreProperty("gnu.io.rxtx.SerialPorts", serialPorts);
            restoreProperty("gnu.io.rxtx.ParallelPorts", parallelPorts);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
