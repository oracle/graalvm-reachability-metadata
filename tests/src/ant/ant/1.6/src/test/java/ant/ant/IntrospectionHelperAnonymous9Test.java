/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IntrospectionHelperAnonymous9Test {
    @TempDir
    File directory;

    @Test
    void resolvesFileAttributeAgainstProject() {
        Project project = new Project();
        project.setBaseDir(directory);
        ConfigurationBean bean = new ConfigurationBean();
        IntrospectionHelper.getHelper(ConfigurationBean.class).setAttribute(project, bean, "file", "input.txt");
        assertEquals(new File(directory, "input.txt"), bean.file);
    }
}
