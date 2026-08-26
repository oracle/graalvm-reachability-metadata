/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Main;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MainTest {

    @Test
    void configuresRequestedLoggerAndBuildListener(@TempDir Path temporaryDirectory) throws IOException {
        Path buildFile = Files.createFile(temporaryDirectory.resolve("build.xml"));
        Project project = new Project();
        ConfigurableMain main = new ConfigurableMain(new String[] {
            "-buildfile", buildFile.toString(),
            "-logger", DefaultLogger.class.getName(),
            "-listener", DefaultLogger.class.getName()
        });

        main.configureBuildListeners(project);

        assertThat(project.getBuildListeners()).hasSize(2);
        assertThat(Main.getAntVersion()).startsWith("Apache Ant version");
    }

    private static final class ConfigurableMain extends Main {
        private ConfigurableMain(String[] arguments) {
            super(arguments);
        }

        private void configureBuildListeners(Project project) {
            addBuildListeners(project);
        }
    }
}
