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

import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProjectHelperTest {
    @TempDir
    File directory;

    @Test
    void selectsConfiguredParserAndExecutesBuild() throws Exception {
        String key = "org.apache.tools.ant.ProjectHelper";
        String old = System.getProperty(key);
        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        try {
            System.setProperty(key, "org.apache.tools.ant.helper.ProjectHelper2");
            for (ClassLoader loader : new ClassLoader[] {oldLoader, null}) {
                Thread.currentThread().setContextClassLoader(loader);
                File build = new File(directory, "parser.xml");
                Files.writeString(build.toPath(), """
                        <project><target name="verify">
                          <property name="parser.result" value="parsed"/>
                        </target></project>
                        """);
                Project project = TestSupport.project(directory);
                ProjectHelper.getProjectHelper().parse(project, build);
                project.executeTarget("verify");
                assertEquals("parsed", project.getProperty("parser.result"));
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
            if (old == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, old);
            }
        }
    }

    @Test
    void discoversDefaultParser() throws Exception {
        Project project = TestSupport.build(directory, """
                <project><target name="verify">
                  <property name="default.parser" value="ready"/>
                </target></project>
                """);
        assertEquals("ready", project.getProperty("default.parser"));
    }
}
