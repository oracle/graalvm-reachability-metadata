/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.AntStructure;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.apache.tools.ant.types.Reference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AntStructureTest {
    @Test
    void writesDefinitionsForReferenceTypesAndEnumeratedAttributes(@TempDir Path temporaryDirectory)
            throws IOException {
        Project project = new Project();
        project.addDataTypeDefinition("test-reference", Reference.class);
        project.addTaskDefinition("structured-task", EnumeratedTask.class);

        Path output = temporaryDirectory.resolve("ant-structure.dtd");
        AntStructure antStructure = new AntStructure();
        antStructure.setProject(project);
        antStructure.setTaskName("antstructure");
        antStructure.setOutput(output.toFile());

        antStructure.execute();

        String dtd = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(dtd).contains(
                "<!ELEMENT test-reference EMPTY>",
                "refid IDREF #IMPLIED",
                "<!ELEMENT structured-task EMPTY>",
                "mode (alpha | beta) #IMPLIED");
    }

    public static final class EnumeratedTask extends Task {
        public void setMode(StructureMode mode) {
        }
    }

    public static final class StructureMode extends EnumeratedAttribute {
        public StructureMode() {
        }

        @Override
        public String[] getValues() {
            return new String[] {"alpha", "beta"};
        }
    }
}
