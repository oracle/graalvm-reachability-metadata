/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_metadata;

import org.apache.maven.api.metadata.SnapshotVersion;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnapshotVersionTest {
    @Test
    void buildsAndUpdatesResolvedSnapshotArtifacts() {
        SnapshotVersion original = SnapshotVersion.newBuilder().classifier("sources").extension("jar")
                .version("2.0-20250120.101112-7").updated("20250120101112").build();
        SnapshotVersion updated = original.with().version("2.0-20250121.121314-8")
                .updated("20250121121314").build();

        assertEquals("sources", updated.getClassifier());
        assertEquals("jar", updated.getExtension());
        assertEquals("2.0-20250121.121314-8", updated.getVersion());
        assertEquals("20250121121314", updated.getUpdated());
        assertEquals("2.0-20250120.101112-7", original.getVersion());
        assertEquals("20250120101112", original.getUpdated());
    }

    @Test
    void fluentCopiesKeepTheOriginalSubArtifactUnchanged() {
        SnapshotVersion original = SnapshotVersion.newInstance().withClassifier("sources")
                .withExtension("jar").withVersion("2.0-20250120.101112-7").withUpdated("20250120101112");
        SnapshotVersion updated = SnapshotVersion.newBuilder(original, true).build()
                .withClassifier("docs").withExtension("zip")
                .withVersion("2.0-20250121.121314-8").withUpdated("20250121121314");

        assertEquals("docs", updated.getClassifier());
        assertEquals("zip", updated.getExtension());
        assertEquals("2.0-20250121.121314-8", updated.getVersion());
        assertEquals("20250121121314", updated.getUpdated());
        assertEquals("sources", original.getClassifier());
        assertEquals("jar", original.getExtension());
        assertEquals("2.0-20250120.101112-7", original.getVersion());
        assertEquals("20250120101112", original.getUpdated());
    }

    @Test
    void valueEqualityAllowsDeduplicatingSnapshotVersions() {
        SnapshotVersion original = SnapshotVersion.newBuilder().classifier("sources").extension("jar")
                .version("2.0-20250120.101112-7").updated("20250120101112").build();
        SnapshotVersion copy = SnapshotVersion.newBuilder(original, true).build();
        Set<SnapshotVersion> versions = new HashSet<>();
        versions.add(original);
        versions.add(copy);

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertEquals(1, versions.size());
        assertTrue(versions.contains(copy));
        assertNotEquals(original, original.withClassifier("docs"));
        assertNotEquals(original, original.withExtension("zip"));
        assertNotEquals(original, original.withVersion("2.0-20250121.121314-8"));
        assertNotEquals(original, original.withUpdated("20250121121314"));
    }
}
