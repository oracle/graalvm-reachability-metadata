/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.utils.InfoCmp;
import org.jline.utils.InfoCmp.Capability;
import org.junit.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class InfoCmpTest {

    @Test
    public void loadsAndParsesBundledXtermCapabilities() {
        String definition = InfoCmp.getLoadedInfoCmp("xterm-256color");
        Set<Capability> booleans = EnumSet.noneOf(Capability.class);
        Map<Capability, Integer> numbers = new EnumMap<>(Capability.class);
        Map<Capability, String> strings = new EnumMap<>(Capability.class);

        assertNotNull(definition);
        InfoCmp.parseInfoCmp(definition, booleans, numbers, strings);

        assertTrue(booleans.contains(Capability.auto_right_margin));
        assertEquals(Integer.valueOf(256), numbers.get(Capability.max_colors));
        assertNotNull(strings.get(Capability.cursor_address));
    }
}
