/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries.org_apache_aries_util;

import org.apache.aries.util.VersionRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InternalMessageUtilTest {
    @Test
    void reportsMissingVersionWithLocalizedMessage() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> VersionRange.parseVersionRange(null));

        assertEquals("UTIL0010E: Unable to parse the version. Could not parse empty string.",
                exception.getMessage());
    }

    @Test
    void formatsRangeInExactVersionDiagnostic() {
        String range = "[2.0.0,3.0.0]";
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> VersionRange.parseVersionRange(range, true));

        assertEquals("UTIL0011E: Unable to parse " + range
                + " for the exact version. Could not parse " + range + ".", exception.getMessage());
    }
}
