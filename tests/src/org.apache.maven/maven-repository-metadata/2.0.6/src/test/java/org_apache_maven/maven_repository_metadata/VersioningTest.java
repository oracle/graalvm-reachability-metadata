/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_repository_metadata;

import org.apache.maven.artifact.repository.metadata.Versioning;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VersioningTest {
    @Test
    void formatsRepositoryUpdateTimestampInUtc() {
        Versioning versioning = new Versioning();
        versioning.setLastUpdatedTimestamp(Date.from(Instant.parse("2025-03-04T23:06:07-05:00")));
        assertEquals("20250305040607", versioning.getLastUpdated());

        versioning.setLastUpdatedTimestamp(Date.from(Instant.parse("2025-01-02T03:04:05Z")));
        assertEquals("20250102030405", versioning.getLastUpdated());
    }
}
