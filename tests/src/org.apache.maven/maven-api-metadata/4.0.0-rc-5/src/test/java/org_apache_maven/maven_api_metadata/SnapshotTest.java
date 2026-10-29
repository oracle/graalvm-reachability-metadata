/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_metadata;

import org.apache.maven.api.metadata.Snapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnapshotTest {
    @Test
    void startsWithNoDeploymentOrLocalCopy() {
        Snapshot snapshot = Snapshot.newInstance();

        assertNull(snapshot.getTimestamp());
        assertEquals(0, snapshot.getBuildNumber());
        assertFalse(snapshot.isLocalCopy());
    }

    @Test
    void derivesANewDeploymentWithoutChangingThePreviousOne() {
        Snapshot original = Snapshot.newBuilder().timestamp("20250120.101112")
                .buildNumber(7).localCopy(false).build();
        Snapshot updated = original.with().timestamp("20250121.121314").buildNumber(8).build();

        assertEquals("20250121.121314", updated.getTimestamp());
        assertEquals(8, updated.getBuildNumber());
        assertFalse(updated.isLocalCopy());
        assertEquals("20250120.101112", original.getTimestamp());
        assertEquals(7, original.getBuildNumber());
    }

    @Test
    void copiesCanSwitchLocalCopyAndResetPrimitiveValues() {
        Snapshot original = Snapshot.newInstance(false).withTimestamp("20250120.101112")
                .withBuildNumber(7).withLocalCopy(true);
        Snapshot updated = Snapshot.newBuilder(original, true).build()
                .withTimestamp("20250121.121314").withBuildNumber(0).withLocalCopy(false);
        Snapshot rebuilt = Snapshot.newBuilder(original).buildNumber(0).localCopy(false).build();

        assertEquals("20250121.121314", updated.getTimestamp());
        assertEquals(0, updated.getBuildNumber());
        assertFalse(updated.isLocalCopy());
        assertEquals("20250120.101112", rebuilt.getTimestamp());
        assertEquals(0, rebuilt.getBuildNumber());
        assertFalse(rebuilt.isLocalCopy());
        assertTrue(original.isLocalCopy());
        assertEquals(7, original.getBuildNumber());
    }
}
