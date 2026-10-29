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

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IntrospectionHelperAnonymous9Test {
    @TempDir
    File directory;

    @Test
    void resolvesFileAttributeAgainstProject() throws Exception {
        Project project = new Project();
        project.setBaseDir(directory);
        File input = new File(directory, "input.properties");
        Files.writeString(input.toPath(), "build.kind=release\n");
        Property property = new Property();
        property.setProject(project);
        IntrospectionHelper.getHelper(Property.class).setAttribute(project, property, "file", "input.properties");
        assertEquals(input, property.getFile());
        property.execute();
        assertEquals("release", project.getProperty("build.kind"));
    }
}
