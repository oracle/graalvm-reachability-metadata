/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries.org_apache_aries_util;

import org.apache.aries.util.VersionRange;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Org_apache_aries_utilTest {
    @Test
    void matchesRangeBoundaries() {
        VersionRange range = VersionRange.parseVersionRange("[2.0.0,3.0.0)");

        assertTrue(range.matches(new Version("2.0.0")));
        assertTrue(range.matches(new Version("2.5.0")));
        assertFalse(range.matches(new Version("1.9.0")));
        assertFalse(range.matches(new Version("3.0.0")));
    }

    @Test
    void rejectsExclusiveExactVersionWithLocalizedMessage() {
        String range = "[2.0.0,2.0.0)";
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> VersionRange.parseVersionRange(range, true));

        assertEquals("UTIL0009E: Unable to parse the version. Could not parse " + range + ".",
                exception.getMessage());
    }
}
