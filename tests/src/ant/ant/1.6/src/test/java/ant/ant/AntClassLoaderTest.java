/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class AntClassLoaderTest {
    private static final String TASK_DEFAULTS = "org/apache/tools/ant/taskdefs/defaults.properties";

    @Test
    void delegatesClassesAndStandardTaskResourcesToItsParent() throws IOException, ClassNotFoundException {
        ClassLoader parent = AntClassLoader.class.getClassLoader();
        AntClassLoader loader = new AntClassLoader(parent, true);

        assertThat(loader.loadClass(Project.class.getName())).isSameAs(Project.class);
        assertThat(loader.forceLoadSystemClass(String.class.getName())).isSameAs(String.class);

        URL parentFirstResource = loader.getResource(TASK_DEFAULTS);
        assertThat(parentFirstResource).isNotNull();

        loader.setParentFirst(false);
        URL loaderFirstResource = loader.getResource(TASK_DEFAULTS);
        assertThat(loaderFirstResource).isEqualTo(parentFirstResource);

        try (InputStream stream = loader.getResourceAsStream(TASK_DEFAULTS)) {
            assertThat(stream).isNotNull();
            assertThat(new String(stream.readAllBytes())).contains("echo=org.apache.tools.ant.taskdefs.Echo");
        }
    }

    @Test
    void initializesAClassThroughTheLoaderUtility() {
        assertThatCode(() -> AntClassLoader.initializeClass(Project.class)).doesNotThrowAnyException();
    }
}
