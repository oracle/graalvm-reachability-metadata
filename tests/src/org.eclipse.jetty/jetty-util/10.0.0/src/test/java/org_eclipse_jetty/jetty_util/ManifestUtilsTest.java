/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.util.Optional;

import org.eclipse.jetty.util.ManifestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManifestUtilsTest {
    @Test
    void readsNamedModuleVersionsWhenNoJarManifestExists() {
        assertTrue(ManifestUtils.getManifest(String.class).isEmpty());
        Optional<String> version = ManifestUtils.getVersion(String.class);
        assertTrue(version.isPresent());
        assertFalse(version.orElseThrow().isBlank());
        assertEquals(String.class.getModule().getDescriptor().rawVersion(), version);
    }
}
