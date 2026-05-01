/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.apache.velocity.test.ClassloaderChangeTest;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ClassloaderChangeTestTest {
    private static final byte[] FOO_CLASS_BYTES = Base64.getDecoder().decode(
            "yv66vgAAADQAEQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+" +
            "AQADKClWCAAIAQAOSGVsbG8gRnJvbSBGb28HAAoBAANGb28BAARDb2RlAQAPTGluZU51" +
            "bWJlclRhYmxlAQAEZG9JdAEAFCgpTGphdmEvbGFuZy9TdHJpbmc7AQAKU291cmNlRmls" +
            "ZQEACEZvby5qYXZhACEACQACAAAAAAACAAEABQAGAAEACwAAAB0AAQABAAAABSq3AAGx" +
            "AAAAAQAMAAAABgABAAAAAQABAA0ADgABAAsAAAAbAAEAAQAAAAMSB7AAAAABAAwAAAAG" +
            "AAEAAAADAAEADwAAAAIAEA=="
    );

    @Test
    void reloadsEquivalentFooClassesFromSeparateClassLoaders() throws Exception {
        writeFooClassForVelocityTestClassLoader();

        try {
            new ClassloaderChangeTest().runTest();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void writeFooClassForVelocityTestClassLoader() throws IOException {
        Path classFile = Path.of("..", "test", "classloader", "Foo.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, FOO_CLASS_BYTES);
    }
}
