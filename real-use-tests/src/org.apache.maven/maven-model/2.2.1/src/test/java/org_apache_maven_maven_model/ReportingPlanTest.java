/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.Model;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.ReportSet;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Maven model's reporting configuration subsystem to build a project site plan.
public class ReportingPlanTest {
    @Test
    @Timeout(60)
    public void catalogsConfiguredReportsAndReportSetsForProjectSite() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.orders</groupId>
                  <artifactId>orders-service</artifactId>
                  <version>1.4.0</version>
                  <reporting>
                    <outputDirectory>target/site</outputDirectory>
                    <excludeDefaults>true</excludeDefaults>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-project-info-reports-plugin</artifactId>
                        <version>3.4.1</version>
                        <reportSets>
                          <reportSet>
                            <id>release-documentation</id>
                            <reports>
                              <report>dependencies</report>
                              <report>scm</report>
                            </reports>
                          </reportSet>
                        </reportSets>
                      </plugin>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-report-plugin</artifactId>
                        <version>3.1.2</version>
                        <reportSets>
                          <reportSet>
                            <id>test-results</id>
                            <reports>
                              <report>report-only</report>
                            </reports>
                          </reportSet>
                        </reportSets>
                      </plugin>
                    </plugins>
                  </reporting>
                </project>
                """);

        assertEquals("com.example.orders", project.getGroupId());
        assertEquals("orders-service", project.getArtifactId());

        Reporting reporting = project.getReporting();
        assertNotNull(reporting);
        assertEquals("target/site", reporting.getOutputDirectory());
        assertTrue(reporting.isExcludeDefaults());
        assertEquals(2, reporting.getPlugins().size());

        ReportPlugin projectInfo = reporting.getPlugins().stream()
                .filter(plugin -> "org.apache.maven.plugins:maven-project-info-reports-plugin".equals(plugin.getKey()))
                .findFirst()
                .orElseThrow();
        assertEquals("3.4.1", projectInfo.getVersion());
        assertEquals("org.apache.maven.plugins:maven-project-info-reports-plugin", projectInfo.getKey());
        assertEquals(1, projectInfo.getReportSets().size());

        ReportSet releaseDocumentation = projectInfo.getReportSets().stream()
                .filter(reportSet -> "release-documentation".equals(reportSet.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, releaseDocumentation.getReports().size());
        assertEquals("dependencies", releaseDocumentation.getReports().get(0));
        assertEquals("scm", releaseDocumentation.getReports().get(1));

        ReportPlugin surefire = reporting.getPlugins().get(1);
        assertEquals("maven-surefire-report-plugin", surefire.getArtifactId());
        assertEquals(1, surefire.getReportSets().size());
        assertEquals("report-only", surefire.getReportSets().get(0).getReports().get(0));
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }
}
