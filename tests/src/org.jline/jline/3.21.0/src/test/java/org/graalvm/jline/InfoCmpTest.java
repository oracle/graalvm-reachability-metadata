/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.utils.InfoCmp;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class InfoCmpTest {

    @Test
    public void loadsBundledAnsiTerminalCapabilities() {
        String capabilities = InfoCmp.getLoadedInfoCmp("ansi");

        assertTrue(capabilities.startsWith("#"));
        assertTrue(capabilities.contains("ansi|ansi/pc-term compatible with color"));
    }
}
