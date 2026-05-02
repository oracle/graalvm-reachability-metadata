/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.email.EmailTask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EmailTaskTest {
    @Test
    void instantiatesUuMailerBeforeValidatingAddresses() {
        EmailTask task = newEmailTask();
        EmailTask.Encoding encoding = new EmailTask.Encoding();
        encoding.setValue(EmailTask.UU);
        task.setEncoding(encoding);

        assertThatThrownBy(task::execute)
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("A from element is required");
    }

    private EmailTask newEmailTask() {
        EmailTask task = new EmailTask();
        task.setProject(newProject());
        task.setTaskName("mail");
        return task;
    }

    private Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }
}
