/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationFile;
import org.apache.maven.model.ActivationOS;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Maven model's environment-based profile activation subsystem to choose a platform build from a project POM.
public class EnvironmentProfileActivationTest {
    @Test
    @Timeout(60)
    public void catalogsPlatformAndMarkerFileActivationRules() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.platform</groupId>
                  <artifactId>orders-service</artifactId>
                  <version>4.0.0</version>
                  <profiles>
                    <profile>
                      <id>linux-container</id>
                      <activation>
                        <os>
                          <name>Linux</name>
                          <family>unix</family>
                          <arch>amd64</arch>
                          <version>5.</version>
                        </os>
                      </activation>
                    </profile>
                    <profile>
                      <id>local-secrets</id>
                      <activation>
                        <file>
                          <exists>${user.home}/.orders-secrets</exists>
                        </file>
                      </activation>
                    </profile>
                    <profile>
                      <id>ci</id>
                      <activation>
                        <property>
                          <name>env.BUILD_PROFILE</name>
                          <value>ci</value>
                        </property>
                      </activation>
                    </profile>
                  </profiles>
                </project>
                """);

        List<ActivationRule> rules = project.getProfiles().stream()
                .map(EnvironmentProfileActivationTest::activationRule)
                .toList();

        assertEquals(List.of(
                new ActivationRule("linux-container", "Linux", "unix", "amd64", "5.", null, null, null),
                new ActivationRule("local-secrets", null, null, null, null,
                        "${user.home}/.orders-secrets", null, null),
                new ActivationRule("ci", null, null, null, null,
                        null, "env.BUILD_PROFILE", "ci")), rules);
        assertFalse(project.getProfiles().get(0).getActivation().isActiveByDefault());
    }

    private static ActivationRule activationRule(Profile profile) {
        Activation activation = profile.getActivation();
        assertNotNull(activation);
        ActivationOS os = activation.getOs();
        ActivationFile file = activation.getFile();
        ActivationProperty property = activation.getProperty();
        return new ActivationRule(
                profile.getId(),
                os == null ? null : os.getName(),
                os == null ? null : os.getFamily(),
                os == null ? null : os.getArch(),
                os == null ? null : os.getVersion(),
                file == null ? null : file.getExists(),
                property == null ? null : property.getName(),
                property == null ? null : property.getValue());
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }

    private record ActivationRule(
            String profile,
            String osName,
            String osFamily,
            String osArch,
            String osVersion,
            String fileExists,
            String propertyName,
            String propertyValue) {
    }
}
