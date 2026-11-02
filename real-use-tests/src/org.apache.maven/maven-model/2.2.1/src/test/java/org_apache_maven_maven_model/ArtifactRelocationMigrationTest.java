/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Relocation;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Maven model's artifact relocation subsystem to direct a dependency catalog from a legacy coordinate to its replacement.
public class ArtifactRelocationMigrationTest {
    @Test
    @Timeout(60)
    public void createsMigrationNoticeFromRelocatedArtifactPom() throws Exception {
        Model legacyArtifact = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example.legacy</groupId>
                  <artifactId>widget-core</artifactId>
                  <version>1.0.0</version>
                  <distributionManagement>
                    <status>relocated</status>
                    <downloadUrl>https://repo.example.com/widgets</downloadUrl>
                    <relocation>
                      <groupId>com.example.platform</groupId>
                      <artifactId>widget-core</artifactId>
                      <version>2.0.0</version>
                      <message>Widget artifacts moved to the platform group.</message>
                    </relocation>
                  </distributionManagement>
                </project>
                """);

        DistributionManagement distribution = legacyArtifact.getDistributionManagement();
        assertNotNull(distribution);
        assertEquals("relocated", distribution.getStatus());
        assertEquals("https://repo.example.com/widgets", distribution.getDownloadUrl());

        Relocation relocation = distribution.getRelocation();
        assertNotNull(relocation);
        MigrationNotice notice = migrationNotice(legacyArtifact, relocation);

        assertEquals("org.example.legacy:widget-core:1.0.0", notice.from());
        assertEquals("com.example.platform:widget-core:2.0.0", notice.to());
        assertEquals("Widget artifacts moved to the platform group.", notice.message());
    }

    private static MigrationNotice migrationNotice(Model legacyArtifact, Relocation relocation) {
        String from = String.join(":", legacyArtifact.getGroupId(), legacyArtifact.getArtifactId(), legacyArtifact.getVersion());
        String to = String.join(":", relocation.getGroupId(), relocation.getArtifactId(), relocation.getVersion());
        return new MigrationNotice(from, to, relocation.getMessage());
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }

    private record MigrationNotice(String from, String to, String message) {
    }
}
