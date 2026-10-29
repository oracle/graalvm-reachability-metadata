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

import org.apache.tools.ant.types.selectors.modifiedselector.ModifiedSelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ModifiedSelectorTest {
    @TempDir
    File directory;

    @Test
    void cachesDigestsAndSelectsChangedFiles() throws Exception {
        File input = new File(directory, "input.txt");
        Files.writeString(input.toPath(), "before");
        ModifiedSelector selector = new ModifiedSelector();
        selector.setProject(TestSupport.project(directory));
        selector.addParam("cache.cachefile", new File(directory, "digests.properties").getAbsolutePath());
        assertTrue(selector.isSelected(directory, "input.txt", input));
        assertFalse(selector.isSelected(directory, "input.txt", input));
        Files.writeString(input.toPath(), "after");
        assertTrue(selector.isSelected(directory, "input.txt", input));
    }
}
