/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import org.eclipse.osgi.framework.internal.core.AliasMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AliasMapperTest {
    @Test
    public void normalizesProcessorAndOperatingSystemAliases() {
        AliasMapper mapper = new AliasMapper();
        assertEquals("x86", mapper.aliasProcessor("I686"));
        assertEquals("x86-64", mapper.aliasProcessor("amd64"));
        assertEquals("PowerPC", mapper.aliasProcessor("ppc"));
        assertEquals("WindowsXP", mapper.aliasOSName("WinXP"));
        assertEquals("MacOSX", mapper.aliasOSName("Mac OS X"));
    }
}
