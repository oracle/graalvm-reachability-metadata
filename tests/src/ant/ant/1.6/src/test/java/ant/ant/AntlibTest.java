/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Antlib;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AntlibTest {
    @TempDir
    File directory;

    @Test
    void parsesAndInstallsTaskDefinitions() throws Exception {
        File definitions = new File(directory, "antlib.xml");
        Files.writeString(definitions.toPath(), """
                <antlib><taskdef name="record" classname="ant.ant.TaskAdapterTest$PropertyTask"/></antlib>
                """);
        Project project = TestSupport.project(directory);
        Antlib.createAntlib(project, definitions.toURI().toURL(), "").execute();
        project.createTask("record").execute();
        assertEquals("executed", project.getProperty("adapter.result"));
    }
}
