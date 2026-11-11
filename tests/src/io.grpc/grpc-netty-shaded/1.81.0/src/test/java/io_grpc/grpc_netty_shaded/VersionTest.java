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

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.util.Version;

public class VersionTest {
    @Test
    void identifiesModulesFromTheirVersionResources() {
        Map<String, Version> versions = Version.identify();
        Version common = versions.get("test-transport");
        assertNotNull(common);
        assertEquals("test-transport", common.artifactId());
        assertFalse(common.artifactVersion().isEmpty());
        assertFalse(common.shortCommitHash().isEmpty());
        assertEquals(versions.keySet(), Version.identify(VersionTest.class.getClassLoader()).keySet());
    }
}
