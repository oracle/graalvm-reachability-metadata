/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.AntStructure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AntStructureTest {
    @TempDir
    File directory;

    @Test
    void emitsDtdWithEnumeratedAttributeValues() throws Exception {
        Project project = new Project();
        project.setBaseDir(directory);
        project.addDataTypeDefinition("configuration", ConfigurationBean.class);
        File output = new File(directory, "build.dtd");
        AntStructure structure = new AntStructure();
        structure.setProject(project);
        structure.setOutput(output);
        structure.execute();
        String dtd = Files.readString(output.toPath());
        assertTrue(dtd.contains("<!ELEMENT configuration"));
        assertTrue(dtd.contains("fast | careful"));
    }
}
