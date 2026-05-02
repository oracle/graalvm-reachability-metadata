/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.rmi.Remote;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Rmic;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class RmicTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void verifyModeLoadsCandidateClassBeforeSkippingUpToDateStub() throws IOException {
        copyClassToTemporaryClasspath(RmicRemoteService.class);
        createUpToDateStubFor(RmicRemoteService.class);

        Rmic rmic = newRmic();
        rmic.setBase(temporaryDirectory.toFile());
        rmic.setClassname(RmicRemoteService.class.getName());
        rmic.setStubVersion("1.2");
        rmic.setVerify(true);

        try {
            rmic.execute();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }

        assertThat(rmic.getLoader()).isNotNull();
        assertThat(rmic.getCompileList()).isEmpty();
    }

    @Test
    void remoteInterfaceResolutionRecognizesDirectRemoteInterface() {
        Rmic rmic = newRmic();

        Class<?> remoteInterface = rmic.getRemoteInterface(RmicRemoteService.class);

        assertThat(remoteInterface).isSameAs(Remote.class);
    }

    private Rmic newRmic() {
        Project project = new Project();
        project.init();

        Rmic rmic = new Rmic();
        rmic.setProject(project);
        rmic.setTaskName("rmic");
        return rmic;
    }

    private void copyClassToTemporaryClasspath(Class<?> type) throws IOException {
        String classResourceName = type.getName().replace('.', '/') + ".class";
        Path classFile = temporaryDirectory.resolve(classResourceName);
        Files.createDirectories(classFile.getParent());

        try (InputStream inputStream = RmicTest.class.getClassLoader()
                .getResourceAsStream(classResourceName)) {
            assertThat(inputStream).isNotNull();
            Files.copy(inputStream, classFile);
        }
    }

    private void createUpToDateStubFor(Class<?> type) throws IOException {
        String stubResourceName = type.getName().replace('.', '/') + "_Stub.class";
        Path stubFile = temporaryDirectory.resolve(stubResourceName);
        Files.createDirectories(stubFile.getParent());
        Files.write(stubFile, new byte[] {0});
        Files.setLastModifiedTime(
                stubFile,
                FileTime.fromMillis(System.currentTimeMillis() + 60_000L));
    }
}

class RmicRemoteService implements Remote {
}
