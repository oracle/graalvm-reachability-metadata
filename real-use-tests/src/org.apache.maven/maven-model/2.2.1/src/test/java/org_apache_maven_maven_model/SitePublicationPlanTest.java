/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Prerequisites;
import org.apache.maven.model.Site;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Maven model's project site publication subsystem to route generated documentation to its configured portal.
public class SitePublicationPlanTest {
    @Test
    @Timeout(60)
    public void plansDocumentationPortalFromProjectPublicationMetadata() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.platform</groupId>
                  <artifactId>orders-api</artifactId>
                  <version>4.0.0</version>
                  <name>Orders API</name>
                  <prerequisites>
                    <maven>2.2.1</maven>
                  </prerequisites>
                  <distributionManagement>
                    <site>
                      <id>engineering-docs</id>
                      <name>Engineering documentation portal</name>
                      <url>https://docs.example.com/orders-api/4.0.0</url>
                    </site>
                  </distributionManagement>
                </project>
                """);

        Prerequisites prerequisites = project.getPrerequisites();
        assertNotNull(prerequisites);
        assertEquals("2.2.1", prerequisites.getMaven());

        DistributionManagement distribution = project.getDistributionManagement();
        assertNotNull(distribution);
        Site site = distribution.getSite();
        assertNotNull(site);
        assertEquals("engineering-docs", site.getId());
        assertEquals("Engineering documentation portal", site.getName());
        assertEquals("https://docs.example.com/orders-api/4.0.0", site.getUrl());
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }
}
