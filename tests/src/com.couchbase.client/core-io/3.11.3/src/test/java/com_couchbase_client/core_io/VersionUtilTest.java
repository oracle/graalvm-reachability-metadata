/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.core.Version;
import com.couchbase.client.core.deps.com.fasterxml.jackson.core.util.VersionUtil;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class VersionUtilTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Version version = VersionUtil.versionFor(MapperConfig.class);
        assertFalse(version.isUnknownVersion());
        assertEquals("jackson-databind", version.getArtifactId());
        assertEquals(new ObjectMapper().version(), version);
    }

    @Test
    @SuppressWarnings("deprecation")
    void readsApplicationMavenVersionDescriptor() {
        Version version = VersionUtil.mavenVersionFor(getClass().getClassLoader(),
                "test.application", "metadata-consumer");
        assertFalse(version.isUnknownVersion());
        assertEquals("test.application", version.getGroupId());
        assertEquals("metadata-consumer", version.getArtifactId());
        assertEquals(2, version.getMajorVersion());
        assertEquals(7, version.getMinorVersion());
        assertEquals(4, version.getPatchLevel());
    }
}
