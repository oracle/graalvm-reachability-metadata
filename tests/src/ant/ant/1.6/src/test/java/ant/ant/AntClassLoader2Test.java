/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class AntClassLoader2Test {
    private static final String LOADER_CLASS_NAME = "org.apache.tools.ant.loader.AntClassLoader2";
    private static final String FIXTURE_CLASS_NAME = "ant.ant.AntClassLoader2Fixture";
    private static final byte[] FIXTURE_CLASS_BYTES = Base64.getMimeDecoder().decode("""
            yv66vgAAAEEAEAoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+
            AQADKClWBwAIAQAeYW50L2FudC9BbnRDbGFzc0xvYWRlcjJGaXh0dXJlAQAEQ29k
            ZQEAD0xpbmVOdW1iZXJUYWJsZQEAEkxvY2FsVmFyaWFibGVUYWJsZQEABHRoaXMB
            ACBMYW50L2FudC9BbnRDbGFzc0xvYWRlcjJGaXh0dXJlOwEAClNvdXJjZUZpbGUB
            ABhBbnRDbGFzc0xvYWRlcjJUZXN0LmphdmEAIAAHAAIAAAAAAAEAAAAFAAYAAQAJ
            AAAALwABAAEAAAAFKrcAAbEAAAACAAoAAAAGAAEAAAA/AAsAAAAMAAEAAAAFAA
            wADQAAAAEADgAAAAIADw==
            """);

    @TempDir
    Path temporaryDirectory;

    @Test
    void projectClassLoaderDefinesClassFromConfiguredPath() throws Exception {
        writeFixtureClassToTemporaryClasspath();

        Project project = new Project();
        org.apache.tools.ant.types.Path classpath = new org.apache.tools.ant.types.Path(project);
        classpath.setLocation(temporaryDirectory.toFile());
        AntClassLoader loader = project.createClassLoader(classpath);

        assertThat(loader.getClass().getName()).isEqualTo(LOADER_CLASS_NAME);
        try {
            Class<?> loadedClass = loader.forceLoadClass(FIXTURE_CLASS_NAME);

            assertThat(loadedClass.getName()).isEqualTo(FIXTURE_CLASS_NAME);
            assertThat(loadedClass.getClassLoader()).isSameAs(loader);
            assertThat(loadedClass.getPackage().getName()).isEqualTo("ant.ant");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private void writeFixtureClassToTemporaryClasspath() throws IOException {
        Path classFile = temporaryDirectory.resolve(
                FIXTURE_CLASS_NAME.replace('.', '/') + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, FIXTURE_CLASS_BYTES);
    }
}
