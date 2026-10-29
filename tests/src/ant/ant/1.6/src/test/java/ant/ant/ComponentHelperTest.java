/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.File;

import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ComponentHelperTest {
    @TempDir
    File directory;

    @Test
    void loadsBuiltInDefinitionsAndRegistersPojo() {
        Project project = TestSupport.project(directory);
        ComponentHelper helper = ComponentHelper.getComponentHelper(project);
        assertInstanceOf(FileSet.class, helper.createComponent("fileset"));
        helper.addTaskDefinition("record", TaskAdapterTest.PropertyTask.class);
        helper.createTask("record").execute();
        assertEquals("executed", project.getProperty("adapter.result"));
    }
}
