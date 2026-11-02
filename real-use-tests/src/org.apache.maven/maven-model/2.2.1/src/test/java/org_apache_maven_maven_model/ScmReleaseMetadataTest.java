/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

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

/// Exercises Maven model's source control metadata subsystem to prepare a release from a project's POM.
public class ScmReleaseMetadataTest {
    @Test
    @Timeout(60)
    public void readsReleaseRepositoryAndTagFromProjectModel() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.platform</groupId>
                  <artifactId>orders-service</artifactId>
                  <version>5.2.0</version>
                  <scm>
                    <connection>scm:git:https://github.com/example/orders-service.git</connection>
                    <developerConnection>scm:git:ssh://git@github.com/example/orders-service.git</developerConnection>
                    <url>https://github.com/example/orders-service</url>
                    <tag>orders-service-5.2.0</tag>
                  </scm>
                </project>
                """);

        Scm sourceControl = project.getScm();
        assertNotNull(sourceControl);
        assertEquals("scm:git:https://github.com/example/orders-service.git", sourceControl.getConnection());
        assertEquals("scm:git:ssh://git@github.com/example/orders-service.git",
                sourceControl.getDeveloperConnection());
        assertEquals("https://github.com/example/orders-service", sourceControl.getUrl());
        assertEquals("orders-service-5.2.0", sourceControl.getTag());
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }
}
