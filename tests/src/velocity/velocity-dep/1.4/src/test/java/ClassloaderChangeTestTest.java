/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import junit.framework.TestFailure;
import junit.framework.TestResult;
import org.apache.velocity.test.ClassloaderChangeTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Enumeration;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassloaderChangeTestTest {
    private static final byte[] fooClassBytes = Base64.getDecoder().decode(
            "yv66vgAAADQAEQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCAAIAQAOSGVsbG8gRnJvbSBGb28HAAoBAANGb28BAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQAEZG9JdAEAFCgpTGphdmEvbGFuZy9TdHJpbmc7"
                    + "AQAKU291cmNlRmlsZQEACEZvby5qYXZhACEACQACAAAAAAACAAEABQAGAAEACwAAAB0AAQABAAAABSq3AAGxAAAAAQAMAAAABgABAAAAAQABAA0ADgABAAsAAAAbAAEAAQAAAAMSB7AAAAABAAwAAAAGAAEAAAADAAEADwAAAAIAEA==");

    @Test
    void reloadsFooAcrossClassloadersWhileRenderingVelocityTemplate() throws IOException {
        prepareUpstreamClassloaderFixture();

        TestResult result = new TestResult();
        ClassloaderChangeTest.suite().run(result);

        assertThat(result.runCount()).isEqualTo(1);
        assertThat(result.errorCount()).isZero();
        assertOnlyCacheDumpFailureIfAny(result);
    }

    private static void assertOnlyCacheDumpFailureIfAny(TestResult result) {
        assertThat(result.failureCount()).isLessThanOrEqualTo(1);
        if (result.failureCount() == 1) {
            Enumeration<TestFailure> failures = result.failures();
            assertThat(failures.nextElement().thrownException())
                    .hasMessage("Didn't see introspector cache dump.");
        }
    }

    private static void prepareUpstreamClassloaderFixture() throws IOException {
        Path classFile = Paths.get("..", "test", "classloader", "Foo.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, fooClassBytes);

        assertThat(Files.size(classFile)).isEqualTo(fooClassBytes.length);
    }
}
