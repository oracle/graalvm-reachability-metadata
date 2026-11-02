/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Maven model's plugin configuration subsystem to build compiler options for a service application.
public class PluginConfigurationPlanTest {
    @Test
    @Timeout(60)
    public void readsCompilerConfigurationForApplicationBuild() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.orders</groupId>
                  <artifactId>orders-service</artifactId>
                  <version>1.4.0</version>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.11.0</version>
                        <configuration>
                          <release>17</release>
                          <parameters>true</parameters>
                          <compilerArgs combine.children="append">
                            <arg>-Xlint:all</arg>
                            <arg>-Werror</arg>
                          </compilerArgs>
                        </configuration>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);

        Plugin compiler = project.getBuild().getPlugins().get(0);
        assertEquals("org.apache.maven.plugins:maven-compiler-plugin", compiler.getKey());
        assertEquals("3.11.0", compiler.getVersion());

        Object rawConfiguration = compiler.getConfiguration();
        assertNotNull(rawConfiguration);
        assertTrue(rawConfiguration instanceof Xpp3Dom);
        Xpp3Dom configuration = (Xpp3Dom) rawConfiguration;

        assertEquals("configuration", configuration.getName());
        assertEquals("17", configuration.getChild("release").getValue());
        assertEquals("true", configuration.getChild("parameters").getValue());

        Xpp3Dom compilerArgs = configuration.getChild("compilerArgs");
        assertNotNull(compilerArgs);
        assertEquals("append", compilerArgs.getAttribute("combine.children"));
        assertEquals(2, compilerArgs.getChildren("arg").length);
        assertEquals("-Xlint:all", compilerArgs.getChildren("arg")[0].getValue());
        assertEquals("-Werror", compilerArgs.getChildren("arg")[1].getValue());
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }
}
