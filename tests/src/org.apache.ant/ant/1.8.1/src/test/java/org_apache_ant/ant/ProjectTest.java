/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.helper.DefaultExecutor;
import org.junit.jupiter.api.Test;

public class ProjectTest {
    @Test
    void configuresProjectsTasksAndProjectAwareObjects() {
        Project project = new Project();
        ProjectHolder holder = new ProjectHolder(project);

        assertThat(Project.getProject(holder)).isSameAs(project);

        ProjectReference reference = new ProjectReference();
        project.setProjectReference(reference);
        assertThat(reference.getProject()).isSameAs(project);

        project.checkTaskClass(RecordingTask.class);

        ConfigurableProject configurableProject = new ConfigurableProject();
        Project subProject = configurableProject.createSubProject();
        assertThat(subProject).isInstanceOf(ConfigurableProject.class);
        assertThat(subProject.getExecutor()).isNotNull();

        Project executorProject = new Project();
        executorProject.setProperty("ant.executor.class", ConfiguredExecutor.class.getName());
        assertThat(executorProject.getExecutor()).isInstanceOf(ConfiguredExecutor.class);
    }

    public static class ConfigurableProject extends Project {
    }

    public static class ConfiguredExecutor extends DefaultExecutor {
    }

    public static class ProjectHolder {
        private final Project project;

        public ProjectHolder(Project project) {
            this.project = project;
        }

        public Project getProject() {
            return project;
        }
    }

    public static class ProjectReference {
        private Project project;

        public Project getProject() {
            return project;
        }

        public void setProject(Project project) {
            this.project = project;
        }
    }

    public static class RecordingTask extends Task {
        @Override
        public void execute() {
        }
    }
}
