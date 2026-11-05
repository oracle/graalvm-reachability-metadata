/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.util.ReleaseInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ReleaseInfoTest {
    @Test
    void readsThePackagedReleaseDescriptor() {
        assertFalse(ReleaseInfo.getVersion().isBlank());
        assertEquals(ReleaseInfo.getAllProperties().getProperty("ImplementationVersion"), ReleaseInfo.getVersion());
    }
}
