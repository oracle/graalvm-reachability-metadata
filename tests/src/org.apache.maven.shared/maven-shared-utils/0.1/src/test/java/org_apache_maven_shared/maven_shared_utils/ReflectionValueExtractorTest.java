/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_shared_utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.shared.utils.introspection.ReflectionValueExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectionValueExtractorTest {
    @Test
    void extractsNestedPropertyWithRootToken() throws Exception {
        Project project = new Project();

        assertEquals("generated", ReflectionValueExtractor.evaluate("project.build.directory", project));
    }

    @Test
    void extractsBooleanPropertyWithoutRootToken() throws Exception {
        assertEquals(Boolean.TRUE, ReflectionValueExtractor.evaluate("build.enabled", new Project(), false));
    }

    @Test
    void extractsIndexedListProperty() throws Exception {
        assertEquals("integration", ReflectionValueExtractor.evaluate("project.build.tasks[1].name", new Project()));
    }

    @Test
    void extractsIndexedArrayProperty() throws Exception {
        assertEquals("compile", ReflectionValueExtractor.evaluate("project.build.taskArray[0].name", new Project()));
    }

    @Test
    void extractsMappedProperty() throws Exception {
        assertEquals("integration", ReflectionValueExtractor.evaluate("project.build.tasksByName(verify).name", new Project()));
    }

    public static class Project {
        private final Build build = new Build();

        public Build getBuild() {
            return build;
        }
    }

    public static class Build {
        private final List<Task> tasks = new ArrayList<>(List.of(new Task("compile"), new Task("integration")));
        private final Map<String, Task> tasksByName = new LinkedHashMap<>();

        public Build() {
            tasksByName.put("verify", tasks.get(1));
        }

        public String getDirectory() {
            return "generated";
        }

        public boolean isEnabled() {
            return true;
        }

        public List<Task> getTasks() {
            return tasks;
        }

        public Task[] getTaskArray() {
            return tasks.toArray(new Task[0]);
        }

        public Map<String, Task> getTasksByName() {
            return tasksByName;
        }
    }

    public static class Task {
        private final String name;

        public Task(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
