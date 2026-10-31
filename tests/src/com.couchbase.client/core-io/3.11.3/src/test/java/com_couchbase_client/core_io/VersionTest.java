/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.util.Version;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VersionTest {
    @Test
    void identifiesPackagedNettyComponents() {
        Map<String, Version> versions = Version.identify(getClass().getClassLoader());
        Version http = versions.get("netty-codec-http");
        assertNotNull(http);
        assertEquals("netty-codec-http", http.artifactId());
        assertFalse(http.artifactVersion().isEmpty());
        assertFalse(http.shortCommitHash().isEmpty());
        assertFalse(http.longCommitHash().isEmpty());
        assertEquals(http.artifactVersion(), Version.identify().get("netty-codec-http").artifactVersion());
    }
}
