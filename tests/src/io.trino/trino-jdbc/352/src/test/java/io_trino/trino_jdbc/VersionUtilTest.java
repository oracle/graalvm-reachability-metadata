/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.TrinoDriver;
import io.trino.jdbc.$internal.jackson.core.Version;
import io.trino.jdbc.$internal.jackson.core.util.VersionUtil;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import io.trino.jdbc.$internal.jackson.databind.cfg.MapperConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionUtilTest {
    @Test
    void returnsUnknownWhenDriverHasNoMavenVersionResource() {
        Version version = VersionUtil.mavenVersionFor(TrinoDriver.class.getClassLoader(), "io.trino", "trino-jdbc");
        assertTrue(version.isUnknownVersion());
    }

    @Test
    void locatesDatabindPackageVersion() {
        Version version = VersionUtil.versionFor(MapperConfig.class);
        assertFalse(version.isUnknownVersion());
        assertEquals("jackson-databind", version.getArtifactId());
        assertEquals(new ObjectMapper().version(), version);
    }
}
