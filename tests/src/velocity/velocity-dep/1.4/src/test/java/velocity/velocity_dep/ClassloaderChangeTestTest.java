/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.apache.velocity.test.ClassloaderChangeTest;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ClassloaderChangeTestTest {
    private static final String FOO_CLASS_BYTES = """
            yv66vgAAADQAEQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQAD
            KClWCAAIAQAOSGVsbG8gRnJvbSBGb28HAAoBAANGb28BAARDb2RlAQAPTGluZU51bWJl
            clRhYmxlAQAEZG9JdAEAFCgpTGphdmEvbGFuZy9TdHJpbmc7AQAKU291cmNlRmlsZQEA
            CEZvby5qYXZhACEACQACAAAAAAACAAEABQAGAAEACwAAAB0AAQABAAAABSq3AAGxAAAA
            AQAMAAAABgABAAAAAQABAA0ADgABAAsAAAAbAAEAAQAAAAMSB7AAAAABAAwAAAAGAAEA
            AAADAAEADwAAAAIAEA==
            """;

    @Test
    void reloadsFooClassAndClearsIntrospectionCacheWhenClassLoaderChanges() throws Exception {
        writeUpstreamFooClassFile();

        try {
            final ClassloaderChangeTest testCase = new ClassloaderChangeTest();
            testCase.runTest();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void writeUpstreamFooClassFile() throws Exception {
        final Path classFile = Path.of("../test/classloader/Foo.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, Base64.getMimeDecoder().decode(FOO_CLASS_BYTES));
    }
}
