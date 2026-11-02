/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Maven model's POM parsing and persistence subsystem to update a service build before publication.
public class PomModelRoundTripTest {
    @Test
    @Timeout(60)
    public void updatesDependencyAndPluginConfigurationWhilePreservingProjectModel() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.orders</groupId>
                  <artifactId>orders-service</artifactId>
                  <version>1.4.0</version>
                  <properties>
                    <java.version>17</java.version>
                    <release.channel>stable</release.channel>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.example</groupId>
                      <artifactId>orders-client</artifactId>
                      <version>3.1.0</version>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
                  <build>
                    <finalName>orders-service-1.4.0</finalName>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.11.0</version>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);

        assertEquals("com.example.orders", project.getGroupId());
        assertEquals("orders-service", project.getArtifactId());
        assertEquals("stable", project.getProperties().getProperty("release.channel"));
        assertEquals(1, project.getDependencies().size());
        Dependency client = project.getDependencies().get(0);
        assertEquals("runtime", client.getScope());

        client.setVersion("3.2.0");
        project.getProperties().setProperty("release.channel", "canary");
        project.getBuild().setFinalName("orders-service-1.4.0-canary");
        Plugin compiler = project.getBuild().getPlugins().get(0);
        compiler.setVersion("3.12.1");

        StringWriter serialized = new StringWriter();
        new MavenXpp3Writer().write(serialized, project);
        Model published = readModel(serialized.toString());

        assertEquals("com.example.orders", published.getGroupId());
        assertEquals("orders-service", published.getArtifactId());
        assertEquals("canary", published.getProperties().getProperty("release.channel"));
        assertEquals("orders-service-1.4.0-canary", published.getBuild().getFinalName());
        assertEquals(1, published.getDependencies().size());
        assertEquals("org.example", published.getDependencies().get(0).getGroupId());
        assertEquals("orders-client", published.getDependencies().get(0).getArtifactId());
        assertEquals("3.2.0", published.getDependencies().get(0).getVersion());
        assertEquals("runtime", published.getDependencies().get(0).getScope());
        assertEquals(1, published.getBuild().getPlugins().size());
        assertEquals("maven-compiler-plugin", published.getBuild().getPlugins().get(0).getArtifactId());
        assertEquals("3.12.1", published.getBuild().getPlugins().get(0).getVersion());
        assertNotNull(published.getModelVersion());
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }
}
