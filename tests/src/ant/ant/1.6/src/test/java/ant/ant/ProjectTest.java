/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ProjectTest {
    @Test
    void validatesAdaptableTaskClassWithPublicConstructor() {
        Project project = new Project();

        assertThatCode(() -> project.checkTaskClass(AdaptableTask.class))
                .doesNotThrowAnyException();
    }

    @Test
    void assignsProjectReferenceToProjectAwareBean() {
        Project project = new Project();
        ProjectAwareBean bean = new ProjectAwareBean();

        project.setProjectReference(bean);

        assertThat(bean.assignedProject).isSameAs(project);
    }

    public static class AdaptableTask {
        public AdaptableTask() {
        }

        public void execute() {
        }
    }

    public static class ProjectAwareBean {
        private Project assignedProject;

        public void setProject(Project project) {
            assignedProject = project;
        }
    }
}
