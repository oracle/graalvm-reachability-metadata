/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Maven model's dependency exclusion subsystem to build a runtime dependency policy for an application.
public class DependencyExclusionPolicyTest {
    @Test
    @Timeout(60)
    public void buildsRuntimePolicyFromDependencyExclusions() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.checkout</groupId>
                  <artifactId>checkout-service</artifactId>
                  <version>2.6.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example.payments</groupId>
                      <artifactId>payments-client</artifactId>
                      <version>5.4.0</version>
                      <scope>runtime</scope>
                      <exclusions>
                        <exclusion>
                          <groupId>org.example.legacy</groupId>
                          <artifactId>legacy-logging</artifactId>
                        </exclusion>
                        <exclusion>
                          <groupId>com.example.metrics</groupId>
                          <artifactId>optional-metrics</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                </project>
                """);

        Dependency runtimeDependency = project.getDependencies().get(0);
        List<DependencyRule> excludedDependencies = runtimeDependency.getExclusions().stream()
                .map(DependencyExclusionPolicyTest::dependencyRule)
                .toList();

        assertEquals("runtime", runtimeDependency.getScope());
        assertEquals("com.example.payments:payments-client:jar", runtimeDependency.getManagementKey());
        assertEquals(List.of(
                new DependencyRule("org.example.legacy:legacy-logging"),
                new DependencyRule("com.example.metrics:optional-metrics")), excludedDependencies);
        assertTrue(excludedDependencies.stream()
                .anyMatch(rule -> rule.coordinate().equals("org.example.legacy:legacy-logging")));
    }

    private static DependencyRule dependencyRule(Exclusion exclusion) {
        return new DependencyRule(exclusion.getGroupId() + ":" + exclusion.getArtifactId());
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }

    private record DependencyRule(String coordinate) {
    }
}
