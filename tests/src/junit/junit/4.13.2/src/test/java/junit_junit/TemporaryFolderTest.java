/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

public class TemporaryFolderTest {

    @Test
    void createsRootAndNestedTemporaryFolders() {
        Result result = JUnitCore.runClasses(TemporaryFolderFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(result.getRunCount()).isEqualTo(1);
    }

    public static class TemporaryFolderFixture {
        @Rule
        public final TemporaryFolder temporaryFolder = new TemporaryFolder();

        public TemporaryFolderFixture() {
        }

        @org.junit.Test
        public void createsFolders() throws Exception {
            File root = temporaryFolder.getRoot();
            File child = temporaryFolder.newFolder();
            File nested = temporaryFolder.newFolder("parent", "child");

            org.junit.Assert.assertTrue(root.isDirectory());
            org.junit.Assert.assertEquals(root, child.getParentFile());
            org.junit.Assert.assertEquals(new File(root, "parent"), nested.getParentFile());
        }
    }
}
