/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.email.EmailTask;
import org.junit.jupiter.api.Test;

public class EmailTaskTest {
    @Test
    void validatesRequiredSenderAfterSelectingMimeMailer() {
        EmailTask task = new EmailTask();
        task.setProject(new Project());
        EmailTask.Encoding encoding = new EmailTask.Encoding();
        encoding.setValue("mime");
        task.setEncoding(encoding);
        task.setMessage("Ant message");
        task.setToList("recipient@example.invalid");
        BuildException exception = assertThrows(BuildException.class, task::execute);
        assertEquals("A from element is required", exception.getMessage());
    }
}
