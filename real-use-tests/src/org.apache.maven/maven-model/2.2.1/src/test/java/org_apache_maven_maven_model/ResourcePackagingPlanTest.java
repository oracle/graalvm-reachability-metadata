/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Resource;
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

/// Exercises Maven model's resource packaging subsystem to plan filtered configuration and unfiltered static assets for an application build.
public class ResourcePackagingPlanTest {
    @Test
    @Timeout(60)
    public void plansMainAndTestResourcesWithFilteringRules() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.orders</groupId>
                  <artifactId>orders-service</artifactId>
                  <version>1.4.0</version>
                  <build>
                    <filters>
                      <filter>src/main/filters/production.properties</filter>
                      <filter>src/main/filters/secrets.properties</filter>
                    </filters>
                    <resources>
                      <resource>
                        <targetPath>config</targetPath>
                        <filtering>true</filtering>
                        <directory>src/main/config</directory>
                        <includes>
                          <include>**/*.properties</include>
                          <include>**/*.yaml</include>
                        </includes>
                        <excludes>
                          <exclude>**/*.secret</exclude>
                        </excludes>
                      </resource>
                      <resource>
                        <targetPath>static</targetPath>
                        <filtering>false</filtering>
                        <directory>src/main/webapp</directory>
                        <includes>
                          <include>**/*.html</include>
                          <include>**/*.css</include>
                        </includes>
                      </resource>
                    </resources>
                    <testResources>
                      <testResource>
                        <targetPath>test-fixtures</targetPath>
                        <filtering>true</filtering>
                        <directory>src/test/fixtures</directory>
                        <includes>
                          <include>**/*.json</include>
                        </includes>
                      </testResource>
                    </testResources>
                  </build>
                </project>
                """);

        Build build = project.getBuild();
        List<ResourcePlan> mainResources = build.getResources().stream()
                .map(ResourcePackagingPlanTest::resourcePlan)
                .toList();
        List<ResourcePlan> testResources = build.getTestResources().stream()
                .map(ResourcePackagingPlanTest::resourcePlan)
                .toList();

        assertEquals(List.of(
                "src/main/filters/production.properties",
                "src/main/filters/secrets.properties"), build.getFilters());
        assertEquals(2, mainResources.size());
        assertEquals(new ResourcePlan(
                "config", true, "src/main/config",
                List.of("**/*.properties", "**/*.yaml"), List.of("**/*.secret")), mainResources.get(0));
        assertEquals(new ResourcePlan(
                "static", false, "src/main/webapp",
                List.of("**/*.html", "**/*.css"), List.of()), mainResources.get(1));
        assertEquals(1, testResources.size());
        assertEquals(new ResourcePlan(
                "test-fixtures", true, "src/test/fixtures",
                List.of("**/*.json"), List.of()), testResources.get(0));
        assertTrue(mainResources.get(0).filtering());
        assertFalse(mainResources.get(1).filtering());
    }

    private static ResourcePlan resourcePlan(Resource resource) {
        return new ResourcePlan(
                resource.getTargetPath(),
                resource.isFiltering(),
                resource.getDirectory(),
                List.copyOf(resource.getIncludes()),
                List.copyOf(resource.getExcludes()));
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }

    private record ResourcePlan(
            String targetPath,
            boolean filtering,
            String directory,
            List<String> includes,
            List<String> excludes) {
    }
}
