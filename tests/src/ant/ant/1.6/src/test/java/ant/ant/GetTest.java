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

import org.apache.tools.ant.taskdefs.Get;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GetTest {
    @TempDir
    File directory;

    @Test
    void downloadsFileUrl() throws Exception {
        File source = new File(directory, "source.txt");
        File destination = new File(directory, "download.txt");
        Files.writeString(source.toPath(), "downloaded contents");
        Get get = new Get();
        get.setProject(TestSupport.project(directory));
        get.setSrc(source.toURI().toURL());
        get.setDest(destination);
        get.execute();
        assertEquals("downloaded contents", Files.readString(destination.toPath()));
    }
}
