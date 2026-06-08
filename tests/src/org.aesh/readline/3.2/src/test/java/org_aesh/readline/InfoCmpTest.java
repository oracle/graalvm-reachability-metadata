/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.utils.InfoCmp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InfoCmpTest {
    @Test
    void defaultTerminalDescriptionIsLoadedFromBundledResource() {
        String terminalDescription = InfoCmp.getDefaultInfoCmp("xterm");

        assertThat(terminalDescription)
                .contains("xterm terminal emulator")
                .contains("clear=");
    }
}
