/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.File;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.taskdefs.Rmic;
import org.apache.tools.ant.taskdefs.rmic.SunRmic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultRmicAdapterInnerRmicFileNameMapperTest {
    @TempDir
    File directory;

    @Test
    void mapsExistingRemoteInterfaceToIiopStubName() {
        Rmic task = new Rmic();
        task.setProject(TestSupport.project(directory));
        task.setBase(directory);
        task.setCompiler("sun");
        task.execute();
        try {
            task.setIiop(true);
            SunRmic adapter = new SunRmic();
            adapter.setRmic(task);
            String input = String.join(File.separator, "java", "rmi", "Remote.class");
            String expected = String.join(File.separator, "java", "rmi", "_Remote_Stub.class");
            assertArrayEquals(new String[] {expected}, adapter.getMapper().mapFileName(input));
        } finally {
            ((AntClassLoader) task.getLoader()).cleanup();
        }
    }
}
