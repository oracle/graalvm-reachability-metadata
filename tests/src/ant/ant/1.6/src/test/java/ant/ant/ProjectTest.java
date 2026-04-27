/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.junit.jupiter.api.Test;

public class ProjectTest {
    @Test
    void validatesPublicTaskClassWithNoArgumentConstructor() {
        Project project = new Project();

        project.checkTaskClass(ValidatedTask.class);

        ValidatedTask task = new ValidatedTask();
        task.setProject(project);
        assertThat(task.getProject()).isSameAs(project);
    }

    @Test
    void setsProjectReferenceOnPlainObjectWithPublicSetter() {
        Project project = new Project();
        ProjectReferenceTarget target = new ProjectReferenceTarget();

        project.setProjectReference(target);

        assertThat(target.project).isSameAs(project);
    }

    public static final class ValidatedTask extends Task {
        public ValidatedTask() {
        }
    }

    public static final class ProjectReferenceTarget {
        private Project project;

        public void setProject(Project project) {
            this.project = project;
        }
    }
}
