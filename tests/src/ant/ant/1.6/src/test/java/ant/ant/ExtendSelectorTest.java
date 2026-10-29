/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.selectors.ExtendSelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ExtendSelectorTest {
    @TempDir
    File directory;

    @Test
    void instantiatesAndConfiguresFilenameSelector() throws Exception {
        File input = new File(directory, "input.txt");
        Files.writeString(input.toPath(), "input");
        ExtendSelector selector = new ExtendSelector();
        selector.setProject(TestSupport.project(directory));
        selector.setClassname("org.apache.tools.ant.types.selectors.FilenameSelector");
        Parameter parameter = new Parameter();
        parameter.setName("name");
        parameter.setValue("*.txt");
        selector.addParam(parameter);
        assertTrue(selector.isSelected(directory, "input.txt", input));
        assertFalse(selector.isSelected(directory, "input.bin", input));
    }
}
