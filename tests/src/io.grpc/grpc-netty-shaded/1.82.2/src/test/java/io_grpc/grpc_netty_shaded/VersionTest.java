/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.util.Version;

public class VersionTest {
    @Test
    void discoversBuildInformationFromAVersionResource() {
        Map<String, Version> versions = Version.identify();
        Version common = versions.get("example-transport");
        assertNotNull(common);
        assertEquals("example-transport", common.artifactId());
        assertFalse(common.artifactVersion().isEmpty());
        assertTrue(common.buildTimeMillis() > 0);
        assertTrue(common.longCommitHash().startsWith(common.shortCommitHash()));
        assertTrue(common.toString().contains(common.artifactVersion()));
    }
}
