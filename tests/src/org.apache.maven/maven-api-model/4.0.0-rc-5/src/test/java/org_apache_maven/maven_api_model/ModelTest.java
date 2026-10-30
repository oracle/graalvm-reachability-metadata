/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_model;

import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.DependencyManagement;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Parent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModelTest {
    @Test
    void buildsProjectWithParentDependenciesAndBuild() {
        Parent parent = Parent.newBuilder()
                .groupId("com.example").artifactId("parent").version("1.0").build();
        Dependency dependency = Dependency.newBuilder()
                .groupId("com.example").artifactId("api").version("2.0").build();
        Build build = Build.newBuilder().defaultGoal("verify").finalName("application").build();
        Model model = Model.newBuilder()
                .modelVersion("4.0.0")
                .parent(parent)
                .groupId("com.example").artifactId("application").version("1.0")
                .dependencies(List.of(dependency))
                .dependencyManagement(DependencyManagement.newBuilder().dependencies(List.of(dependency)).build())
                .build(build)
                .root(true)
                .build();

        assertEquals("com.example:application:jar:1.0", model.getId());
        assertEquals("com.example:parent:pom:1.0", model.getParent().getId());
        assertEquals("com.example:api:jar", model.getDependencies().getFirst().getManagementKey());
        assertEquals(List.of(dependency), model.getDependencyManagement().getDependencies());
        assertEquals("verify", model.getBuild().getDefaultGoal());
        assertEquals("application", model.getBuild().getFinalName());
        assertEquals("UTF-8", model.getModelEncoding());
        assertTrue(model.isRoot());
    }

    @Test
    void copiesProjectCollectionsAndExposesImmutableValues() {
        List<String> subprojects = new ArrayList<>(List.of("api", "service", "cli"));
        Map<String, String> properties = new HashMap<>(Map.of("encoding", "UTF-8", "environment", "test"));
        Model model = Model.newBuilder().artifactId("reactor")
                .subprojects(subprojects).properties(properties).build();
        subprojects.clear();
        properties.put("environment", "production");

        assertEquals(List.of("api", "service", "cli"), model.getSubprojects());
        assertEquals(Map.of("encoding", "UTF-8", "environment", "test"), model.getProperties());
        assertThrows(UnsupportedOperationException.class, () -> model.getSubprojects().add("extra"));
        assertThrows(UnsupportedOperationException.class, () -> model.getProperties().put("extra", "value"));
    }

    @Test
    void derivesUpdatedProjectWithoutChangingOriginal() {
        Model original = Model.newBuilder().groupId("com.example").artifactId("application")
                .version("1.0").properties(Map.of("encoding", "UTF-8")).build();
        Model updated = original.with().version("2.0").name("Application").build();
        Model distribution = updated.withPackaging("pom");

        assertEquals("com.example:application:jar:1.0", original.getId());
        assertEquals("com.example:application:jar:2.0", updated.getId());
        assertEquals("Application", updated.getName());
        assertEquals(original.getProperties(), updated.getProperties());
        assertEquals("com.example:application:pom:2.0", distribution.getId());
        assertEquals("jar", updated.getPackaging());
    }
}
