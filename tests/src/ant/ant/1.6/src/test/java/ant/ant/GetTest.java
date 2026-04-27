/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Get;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GetTest {
    @Test
    void downloadsFileWithBasicAuthenticationHeader(@TempDir Path temporaryDirectory) throws IOException {
        Path source = temporaryDirectory.resolve("source.txt");
        Path destination = temporaryDirectory.resolve("destination.txt");
        String content = "downloaded by get task";
        Files.writeString(source, content, StandardCharsets.UTF_8);

        Get get = newGetTask();
        get.setSrc(source.toUri().toURL());
        get.setDest(destination.toFile());
        get.setUsername("ant-user");
        get.setPassword("ant-password");

        get.execute();

        assertThat(Files.readString(destination, StandardCharsets.UTF_8)).isEqualTo(content);
    }

    private static Get newGetTask() {
        Project project = new Project();
        project.init();

        Get get = new Get();
        get.setProject(project);
        get.setTaskName("get");
        return get;
    }
}
