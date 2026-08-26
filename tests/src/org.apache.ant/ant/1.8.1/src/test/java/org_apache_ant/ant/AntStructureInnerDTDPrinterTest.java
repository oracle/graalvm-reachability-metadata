/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.AntStructure;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AntStructureInnerDTDPrinterTest {
    @Test
    void generatesDtdDeclarationsForEnumAndEnumeratedAttributes(@TempDir Path temporaryDirectory)
            throws IOException {
        Project project = new Project();
        project.initProperties();
        project.addTaskDefinition("attribute-task", AttributeTask.class);

        Path output = temporaryDirectory.resolve("structure.dtd");
        AntStructure structure = new AntStructure();
        structure.setProject(project);
        structure.setOutput(output.toFile());

        structure.execute();

        String dtd = Files.readString(output);
        assertThat(dtd).contains("mode (FAST | SAFE) #IMPLIED");
        assertThat(dtd).contains("format (compact | expanded) #IMPLIED");
    }

    public static class AttributeTask extends Task {
        public void setMode(Mode mode) {
        }

        public void setFormat(Format format) {
        }

        @Override
        public void execute() {
        }
    }

    public enum Mode {
        FAST,
        SAFE
    }

    public static class Format extends EnumeratedAttribute {
        @Override
        public String[] getValues() {
            return new String[] { "compact", "expanded" };
        }
    }
}
