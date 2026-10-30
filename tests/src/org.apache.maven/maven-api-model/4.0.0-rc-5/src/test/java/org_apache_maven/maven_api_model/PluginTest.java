/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_model;

import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.PluginExecution;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginTest {
    @Test
    void configuresPluginExecutionsAndDependenciesInProjectBuild() {
        List<String> goals = new ArrayList<>(List.of("compile", "testCompile"));
        PluginExecution execution = PluginExecution.newBuilder().id("compile-sources")
                .phase("compile").priority(10).goals(goals).build();
        goals.clear();
        Dependency dependency = Dependency.newBuilder()
                .groupId("com.example").artifactId("compiler-extension").version("1.0").build();
        Plugin plugin = Plugin.newBuilder().artifactId("maven-compiler-plugin").version("1.0")
                .extensions("true").inherited("false")
                .executions(List.of(execution)).dependencies(List.of(dependency)).build();
        Build build = Build.newBuilder().plugins(List.of(plugin)).build();
        Plugin configured = build.getPlugins().getFirst();

        assertEquals("org.apache.maven.plugins:maven-compiler-plugin", configured.getKey());
        assertTrue(configured.isExtensions());
        assertFalse(configured.isInherited());
        PluginExecution configuredExecution = configured.getExecutions().getFirst();
        assertEquals("compile-sources", configuredExecution.getId());
        assertEquals("compile", configuredExecution.getPhase());
        assertEquals(10, configuredExecution.getPriority());
        assertEquals(List.of("compile", "testCompile"), configuredExecution.getGoals());
        assertEquals("compiler-extension", configured.getDependencies().getFirst().getArtifactId());
        assertThrows(UnsupportedOperationException.class, () -> configuredExecution.getGoals().clear());
    }

    @Test
    void identifiesPluginByCoordinatesAcrossVersionChanges() {
        Plugin original = Plugin.newBuilder().groupId("com.example.plugins")
                .artifactId("package-plugin").version("1.0").build();
        Plugin updated = original.withVersion("2.0");

        assertEquals("com.example.plugins:package-plugin:1.0", original.getId());
        assertEquals("com.example.plugins:package-plugin:2.0", updated.getId());
        assertEquals(original, updated);
        assertEquals(original.hashCode(), updated.hashCode());
        assertEquals(Plugin.constructKey("com.example.plugins", "package-plugin"), updated.getKey());
        assertFalse(updated.isExtensions());
        assertTrue(updated.isInherited());
    }
}
