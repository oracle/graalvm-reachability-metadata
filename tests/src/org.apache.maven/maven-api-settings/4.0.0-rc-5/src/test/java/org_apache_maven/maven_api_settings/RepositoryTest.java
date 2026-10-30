/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_settings;

import org.apache.maven.api.settings.Repository;
import org.apache.maven.api.settings.RepositoryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RepositoryTest {
    @Test
    void configuresIndependentReleaseAndSnapshotPolicies() {
        RepositoryPolicy releases = RepositoryPolicy.newBuilder()
                .enabled(true).updatePolicy("daily").checksumPolicy("fail").build();
        RepositoryPolicy snapshots = RepositoryPolicy.newBuilder()
                .enabled(false).updatePolicy("always").checksumPolicy("warn").build();
        Repository repository = Repository.newBuilder()
                .id("company").name("Company repository")
                .url("https://repo.example.test/artifacts")
                .releases(releases).snapshots(snapshots).build();

        assertEquals("company", repository.getId());
        assertEquals("Company repository", repository.getName());
        assertEquals("https://repo.example.test/artifacts", repository.getUrl());
        assertEquals("default", repository.getLayout());
        assertTrue(repository.getReleases().isEnabled());
        assertEquals("daily", repository.getReleases().getUpdatePolicy());
        assertEquals("fail", repository.getReleases().getChecksumPolicy());
        assertFalse(repository.getSnapshots().isEnabled());
        assertEquals("always", repository.getSnapshots().getUpdatePolicy());
        assertEquals("warn", repository.getSnapshots().getChecksumPolicy());

        Repository enabled = repository.withSnapshots(snapshots.withEnabled(true));
        assertTrue(enabled.getSnapshots().isEnabled());
        assertFalse(repository.getSnapshots().isEnabled());
        assertSame(releases, enabled.getReleases());
        assertEquals(repository.getId(), enabled.getId());
        assertEquals(repository.getUrl(), enabled.getUrl());
        assertEquals(repository.getLayout(), enabled.getLayout());
    }

    @Test
    void copiesRepositoryAndPolicyBuilders() {
        RepositoryPolicy policy = RepositoryPolicy.newInstance();
        RepositoryPolicy interval = policy.withUpdatePolicy("interval:30").withChecksumPolicy("ignore");
        Repository repository = Repository.newBuilder().id("primary")
                .url("https://repo.example.test/primary").releases(interval).build();
        Repository copy = Repository.newBuilder(repository, true)
                .id("backup").url("https://repo.example.test/backup").build();

        assertTrue(policy.isEnabled());
        assertEquals("interval:30", interval.getUpdatePolicy());
        assertEquals("ignore", interval.getChecksumPolicy());
        assertEquals("primary", repository.getId());
        assertEquals("backup", copy.getId());
        assertEquals("https://repo.example.test/backup", copy.getUrl());
        assertSame(interval, copy.getReleases());
        assertEquals("default", copy.getLayout());
    }
}
