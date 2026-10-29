/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;

public final class TestSupport {
    private TestSupport() {
    }

    public static Project project(File directory) {
        Project project = new Project();
        project.setBaseDir(directory);
        project.init();
        return project;
    }

    public static Project build(File directory, String xml) throws IOException {
        File build = new File(directory, "build.xml");
        Files.writeString(build.toPath(), xml);
        Project project = project(directory);
        ProjectHelper.configureProject(project, build);
        project.executeTarget("verify");
        return project;
    }
}
