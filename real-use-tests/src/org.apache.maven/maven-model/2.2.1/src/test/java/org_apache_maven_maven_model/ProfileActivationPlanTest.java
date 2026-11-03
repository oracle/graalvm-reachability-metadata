/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.Activation;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Maven model's profile activation subsystem to select a CI build plan from a project POM.
public class ProfileActivationPlanTest {
    @Test
    @Timeout(60)
    public void selectsCiProfileAndReadsItsBuildAndRepositoryPlan() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.orders</groupId>
                  <artifactId>orders-service</artifactId>
                  <version>1.4.0</version>
                  <profiles>
                    <profile>
                      <id>ci</id>
                      <activation>
                        <property>
                          <name>env.BUILD_PROFILE</name>
                          <value>ci</value>
                        </property>
                      </activation>
                      <properties>
                        <quality.gate>strict</quality.gate>
                      </properties>
                      <repositories>
                        <repository>
                          <id>ci-snapshots</id>
                          <url>https://repo.example.com/ci-snapshots</url>
                          <releases>
                            <enabled>false</enabled>
                          </releases>
                          <snapshots>
                            <enabled>true</enabled>
                            <updatePolicy>always</updatePolicy>
                          </snapshots>
                        </repository>
                      </repositories>
                      <build>
                        <finalName>orders-service-ci</finalName>
                        <plugins>
                          <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-surefire-plugin</artifactId>
                            <version>3.1.2</version>
                          </plugin>
                        </plugins>
                      </build>
                    </profile>
                    <profile>
                      <id>developer</id>
                      <activation>
                        <activeByDefault>true</activeByDefault>
                      </activation>
                      <properties>
                        <quality.gate>quick</quality.gate>
                      </properties>
                    </profile>
                  </profiles>
                </project>
                """);

        Profile ci = profileWithId(project, "ci");
        Activation activation = ci.getActivation();
        assertNotNull(activation);
        assertEquals("env.BUILD_PROFILE", activation.getProperty().getName());
        assertEquals("ci", activation.getProperty().getValue());
        assertFalse(activation.isActiveByDefault());
        assertEquals("strict", ci.getProperties().getProperty("quality.gate"));

        BuildBase build = ci.getBuild();
        assertNotNull(build);
        assertEquals("orders-service-ci", build.getFinalName());
        Plugin testPlugin = build.getPlugins().get(0);
        assertEquals("org.apache.maven.plugins", testPlugin.getGroupId());
        assertEquals("maven-surefire-plugin", testPlugin.getArtifactId());
        assertEquals("3.1.2", testPlugin.getVersion());

        Repository repository = ci.getRepositories().get(0);
        assertEquals("ci-snapshots", repository.getId());
        assertEquals("https://repo.example.com/ci-snapshots", repository.getUrl());
        assertFalse(repository.getReleases().isEnabled());
        assertEquals("always", repository.getSnapshots().getUpdatePolicy());
    }

    private static Profile profileWithId(Model project, String id) {
        return project.getProfiles().stream()
                .filter(profile -> id.equals(profile.getId()))
                .findFirst()
                .orElseThrow();
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }
}
