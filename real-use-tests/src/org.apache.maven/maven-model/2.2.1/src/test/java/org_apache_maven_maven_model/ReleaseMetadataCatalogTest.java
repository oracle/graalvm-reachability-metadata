/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Maven model's release metadata subsystem to catalog a component before publishing it.
public class ReleaseMetadataCatalogTest {
    @Test
    @Timeout(60)
    public void catalogsReleaseRepositoriesSourceAndLicenseFromProjectModel() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.payments</groupId>
                  <artifactId>payment-client</artifactId>
                  <version>2.5.0</version>
                  <packaging>jar</packaging>
                  <name>Payment Client</name>
                  <description>Client for the payments service.</description>
                  <url>https://example.com/payment-client</url>
                  <licenses>
                    <license>
                      <name>Apache License, Version 2.0</name>
                      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                      <distribution>repo</distribution>
                    </license>
                  </licenses>
                  <scm>
                    <connection>scm:git:https://example.com/payment-client.git</connection>
                    <developerConnection>scm:git:ssh://git@example.com/payment-client.git</developerConnection>
                    <url>https://example.com/payment-client/tree/v2.5.0</url>
                    <tag>payment-client-2.5.0</tag>
                  </scm>
                  <distributionManagement>
                    <repository>
                      <id>central</id>
                      <name>Central Releases</name>
                      <url>https://repo.example.com/releases</url>
                    </repository>
                    <snapshotRepository>
                      <id>snapshots</id>
                      <name>Internal Snapshots</name>
                      <url>https://repo.example.com/snapshots</url>
                    </snapshotRepository>
                  </distributionManagement>
                </project>
                """);

        assertEquals("com.example.payments", project.getGroupId());
        assertEquals("payment-client", project.getArtifactId());
        assertEquals("2.5.0", project.getVersion());
        assertEquals("jar", project.getPackaging());
        assertEquals("Payment Client", project.getName());

        assertEquals(1, project.getLicenses().size());
        License license = project.getLicenses().get(0);
        assertEquals("Apache License, Version 2.0", license.getName());
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0.txt", license.getUrl());
        assertEquals("repo", license.getDistribution());

        Scm sourceControl = project.getScm();
        assertNotNull(sourceControl);
        assertEquals("scm:git:https://example.com/payment-client.git", sourceControl.getConnection());
        assertEquals("payment-client-2.5.0", sourceControl.getTag());

        DistributionManagement distribution = project.getDistributionManagement();
        assertNotNull(distribution);
        DeploymentRepository releases = distribution.getRepository();
        DeploymentRepository snapshots = distribution.getSnapshotRepository();
        assertEquals("central", releases.getId());
        assertEquals("https://repo.example.com/releases", releases.getUrl());
        assertEquals("snapshots", snapshots.getId());
        assertEquals("https://repo.example.com/snapshots", snapshots.getUrl());
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }
}
