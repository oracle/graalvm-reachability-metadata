/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.env.VersionAndGitHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionAndGitHashTest {
    @Test
    void exercisesPublicApi() throws Exception {
        VersionAndGitHash version = VersionAndGitHash.from(Core.class);
        assertNotSame(VersionAndGitHash.UNKNOWN, version);
        assertFalse(version.version().isBlank());
        assertFalse(version.gitHash().isBlank());
        assertTrue(version.toString().contains(version.version()));
    }
}
