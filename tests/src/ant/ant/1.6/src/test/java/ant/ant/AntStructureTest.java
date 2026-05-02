/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.AntStructure;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class AntStructureTest {
    @Test
    void writesDtdForTaskWithEnumeratedAttribute(@TempDir Path tempDir) throws Exception {
        Project project = new Project();
        project.addTaskDefinition("structured", StructuredTask.class);
        Path output = tempDir.resolve("ant-structure.dtd");
        AntStructure task = new AntStructure();
        task.setProject(project);
        task.setOutput(output.toFile());

        task.execute();

        String dtd = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(dtd)
                .contains("<!ENTITY % tasks \"structured\">")
                .contains("<!ELEMENT structured EMPTY>")
                .contains("          mode (strict | lenient) #IMPLIED")
                .contains("          enabled %boolean; #IMPLIED");
    }

    public static class StructuredTask extends Task {
        private boolean enabled;
        private Mode mode;

        public StructuredTask() {
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        @Override
        public void execute() {
        }
    }

    public static class Mode extends EnumeratedAttribute {
        public Mode() {
        }

        @Override
        public String[] getValues() {
            return new String[] {"strict", "lenient"};
        }
    }
}
