/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.Build;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Maven model's build lifecycle subsystem to turn plugin executions into a CI task plan.
public class BuildLifecyclePlanTest {
    @Test
    @Timeout(60)
    public void plansCompilerAndVerificationTasksFromPluginExecutions() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.orders</groupId>
                  <artifactId>orders-service</artifactId>
                  <version>1.4.0</version>
                  <build>
                    <extensions>
                      <extension>
                        <groupId>org.example.build</groupId>
                        <artifactId>company-build-extension</artifactId>
                        <version>2.3.0</version>
                      </extension>
                    </extensions>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.11.0</version>
                        <executions>
                          <execution>
                            <id>compile-production-sources</id>
                            <phase>compile</phase>
                            <goals>
                              <goal>compile</goal>
                            </goals>
                          </execution>
                          <execution>
                            <id>compile-test-sources</id>
                            <phase>test-compile</phase>
                            <goals>
                              <goal>testCompile</goal>
                            </goals>
                            <inherited>false</inherited>
                          </execution>
                        </executions>
                      </plugin>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <version>3.1.2</version>
                        <executions>
                          <execution>
                            <id>unit-tests</id>
                            <phase>test</phase>
                            <goals>
                              <goal>test</goal>
                            </goals>
                          </execution>
                        </executions>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);

        Build build = project.getBuild();
        assertNotNull(build);
        assertEquals(1, build.getExtensions().size());
        Extension extension = build.getExtensions().get(0);
        assertEquals("org.example.build:company-build-extension", extension.getKey());
        assertEquals("2.3.0", extension.getVersion());

        List<LifecycleTask> tasks = lifecycleTasks(build);

        assertEquals(3, tasks.size());
        assertEquals(new LifecycleTask(
                "org.apache.maven.plugins:maven-compiler-plugin",
                "compile-production-sources", "compile", List.of("compile"), null), tasks.get(0));
        assertEquals(new LifecycleTask(
                "org.apache.maven.plugins:maven-compiler-plugin",
                "compile-test-sources", "test-compile", List.of("testCompile"), "false"), tasks.get(1));
        assertEquals(new LifecycleTask(
                "org.apache.maven.plugins:maven-surefire-plugin",
                "unit-tests", "test", List.of("test"), null), tasks.get(2));
        assertTrue(tasks.stream().anyMatch(task -> "test".equals(task.phase())));
    }

    private static List<LifecycleTask> lifecycleTasks(Build build) {
        List<LifecycleTask> tasks = new ArrayList<>();
        for (Plugin plugin : build.getPlugins()) {
            for (PluginExecution execution : plugin.getExecutions()) {
                tasks.add(new LifecycleTask(
                        plugin.getKey(),
                        execution.getId(),
                        execution.getPhase(),
                        List.copyOf(execution.getGoals()),
                        execution.getInherited()));
            }
        }
        return List.copyOf(tasks);
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }

    private record LifecycleTask(
            String plugin,
            String execution,
            String phase,
            List<String> goals,
            String inherited) {
    }
}
