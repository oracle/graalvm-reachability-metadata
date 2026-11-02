/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Maven model's plugin repository subsystem to choose trusted artifact sources for a CI build.
public class PluginRepositoryPolicyTest {
    @Test
    @Timeout(60)
    public void buildsPluginRepositoryPlanWithSeparateReleaseAndSnapshotPolicies() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.platform</groupId>
                  <artifactId>orders-service</artifactId>
                  <version>4.0.0</version>
                  <repositories>
                    <repository>
                      <id>central-artifacts</id>
                      <url>https://repo.example.com/artifacts</url>
                    </repository>
                  </repositories>
                  <pluginRepositories>
                    <pluginRepository>
                      <id>company-build-plugins</id>
                      <name>Company build plugins</name>
                      <url>https://repo.example.com/build-plugins</url>
                      <releases>
                        <enabled>true</enabled>
                        <updatePolicy>daily</updatePolicy>
                        <checksumPolicy>fail</checksumPolicy>
                      </releases>
                      <snapshots>
                        <enabled>false</enabled>
                        <updatePolicy>never</updatePolicy>
                        <checksumPolicy>fail</checksumPolicy>
                      </snapshots>
                    </pluginRepository>
                    <pluginRepository>
                      <id>preview-build-plugins</id>
                      <url>https://repo.example.com/build-plugins-preview</url>
                      <releases>
                        <enabled>false</enabled>
                      </releases>
                      <snapshots>
                        <enabled>true</enabled>
                        <updatePolicy>always</updatePolicy>
                        <checksumPolicy>warn</checksumPolicy>
                      </snapshots>
                    </pluginRepository>
                  </pluginRepositories>
                </project>
                """);

        List<RepositoryPlan> pluginRepositories = pluginRepositoryPlan(project);

        assertEquals(1, project.getRepositories().size());
        assertEquals("central-artifacts", project.getRepositories().get(0).getId());
        assertEquals(2, pluginRepositories.size());
        assertEquals(new RepositoryPlan(
                "company-build-plugins",
                "Company build plugins",
                "https://repo.example.com/build-plugins",
                true,
                "daily",
                "fail",
                false,
                "never",
                "fail"), pluginRepositories.get(0));
        assertEquals(new RepositoryPlan(
                "preview-build-plugins",
                null,
                "https://repo.example.com/build-plugins-preview",
                false,
                null,
                null,
                true,
                "always",
                "warn"), pluginRepositories.get(1));
        assertTrue(pluginRepositories.get(0).releasesEnabled());
        assertFalse(pluginRepositories.get(0).snapshotsEnabled());
        assertTrue(pluginRepositories.get(1).snapshotsEnabled());
    }

    private static List<RepositoryPlan> pluginRepositoryPlan(Model project) {
        return project.getPluginRepositories().stream()
                .map(PluginRepositoryPolicyTest::repositoryPlan)
                .toList();
    }

    private static RepositoryPlan repositoryPlan(Repository repository) {
        RepositoryPolicy releases = repository.getReleases();
        RepositoryPolicy snapshots = repository.getSnapshots();
        return new RepositoryPlan(
                repository.getId(),
                repository.getName(),
                repository.getUrl(),
                releases.isEnabled(),
                releases.getUpdatePolicy(),
                releases.getChecksumPolicy(),
                snapshots.isEnabled(),
                snapshots.getUpdatePolicy(),
                snapshots.getChecksumPolicy());
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }

    private record RepositoryPlan(
            String id,
            String name,
            String url,
            boolean releasesEnabled,
            String releasesUpdatePolicy,
            String releasesChecksumPolicy,
            boolean snapshotsEnabled,
            String snapshotsUpdatePolicy,
            String snapshotsChecksumPolicy) {
    }
}
