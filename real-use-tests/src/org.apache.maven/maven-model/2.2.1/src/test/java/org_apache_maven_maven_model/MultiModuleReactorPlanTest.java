/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Maven model's multi-module reactor subsystem to plan child projects from an aggregator POM.
public class MultiModuleReactorPlanTest {
    @Test
    @Timeout(60)
    public void plansModulesUsingParentDependencyAndPluginManagement() throws Exception {
        Model reactor = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example.build</groupId>
                    <artifactId>company-parent</artifactId>
                    <version>8.2.0</version>
                    <relativePath>../build-parent/pom.xml</relativePath>
                  </parent>
                  <groupId>com.example.platform</groupId>
                  <artifactId>commerce-reactor</artifactId>
                  <version>4.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>orders-api</module>
                    <module>orders-service</module>
                    <module>orders-cli</module>
                  </modules>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example.platform</groupId>
                        <artifactId>orders-api</artifactId>
                        <version>4.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <build>
                    <pluginManagement>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.11.0</version>
                        </plugin>
                      </plugins>
                    </pluginManagement>
                  </build>
                </project>
                """);

        assertEquals("com.example.platform", reactor.getGroupId());
        assertEquals("commerce-reactor", reactor.getArtifactId());
        assertEquals("pom", reactor.getPackaging());
        assertEquals(List.of("orders-api", "orders-service", "orders-cli"), reactor.getModules());

        Parent parent = reactor.getParent();
        assertNotNull(parent);
        assertEquals("com.example.build", parent.getGroupId());
        assertEquals("company-parent", parent.getArtifactId());
        assertEquals("8.2.0", parent.getVersion());
        assertEquals("../build-parent/pom.xml", parent.getRelativePath());

        DependencyManagement dependencyManagement = reactor.getDependencyManagement();
        assertNotNull(dependencyManagement);
        Dependency managedApi = dependencyManagement.getDependencies().get(0);
        assertEquals("com.example.platform", managedApi.getGroupId());
        assertEquals("orders-api", managedApi.getArtifactId());
        assertEquals("4.0.0", managedApi.getVersion());

        PluginManagement pluginManagement = reactor.getBuild().getPluginManagement();
        assertNotNull(pluginManagement);
        Plugin compiler = pluginManagement.getPlugins().get(0);
        assertEquals("maven-compiler-plugin", compiler.getArtifactId());
        assertEquals("3.11.0", compiler.getVersion());
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }
}
