/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecuteJava;
import org.apache.tools.ant.types.Commandline;
import org.junit.jupiter.api.Test;

public class ExecuteJavaTest {
    @Test
    void invokesApplicationMainWithArguments() {
        Application.result = null;
        Commandline command = new Commandline();
        command.setExecutable(Application.class.getName());
        command.createArgument().setValue("first");
        command.createArgument().setValue("second");
        ExecuteJava execution = new ExecuteJava();
        execution.setJavaCommand(command);
        execution.setTimeout(30000L);
        execution.execute(new Project());
        assertFalse(execution.killedProcess());
        assertEquals("first/second", Application.result);
    }

    public static class Application {
        static volatile String result;

        public static void main(String[] arguments) {
            result = String.join("/", arguments);
        }
    }
}
