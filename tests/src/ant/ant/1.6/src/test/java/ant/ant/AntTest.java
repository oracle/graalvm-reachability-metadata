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
import org.apache.tools.ant.taskdefs.Ant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AntTest {
    @BeforeEach
    void resetRecordingReference() {
        RecordingReference.clonedFrom = null;
        RecordingReference.assignedReference = null;
        RecordingReference.assignedProject = null;
    }

    @Test
    void clonesAndConfiguresCopiedReferenceForSubproject(@TempDir Path temporaryDirectory) throws IOException {
        Path buildFile = temporaryDirectory.resolve("child-build.xml");
        Files.writeString(buildFile, childBuildFile(), StandardCharsets.UTF_8);

        Project parentProject = new Project();
        parentProject.init();
        parentProject.setBaseDir(temporaryDirectory.toFile());
        RecordingReference originalReference = new RecordingReference("parent-reference");
        parentProject.addReference("recording-reference", originalReference);

        Ant antTask = new Ant();
        antTask.setProject(parentProject);
        antTask.setTaskName("ant");
        antTask.init();
        antTask.setDir(temporaryDirectory.toFile());
        antTask.setAntfile(buildFile.getFileName().toString());
        antTask.setTarget("verify");
        antTask.addReference(copiedReference());

        antTask.execute();

        assertThat(RecordingReference.clonedFrom).isSameAs(originalReference);
        assertThat(RecordingReference.assignedReference).isNotSameAs(originalReference);
        assertThat(RecordingReference.assignedProject).isNotNull();
        assertThat(RecordingReference.assignedProject).isNotSameAs(parentProject);
        assertThat(originalReference.project).isNull();
    }

    private static Ant.Reference copiedReference() {
        Ant.Reference reference = new Ant.Reference();
        reference.setRefId("recording-reference");
        reference.setToRefid("copied-recording-reference");
        return reference;
    }

    private static String childBuildFile() {
        return """
            <project name="child" default="verify">
                <target name="verify"/>
            </project>
            """;
    }

    public static final class RecordingReference implements Cloneable {
        private static RecordingReference clonedFrom;
        private static RecordingReference assignedReference;
        private static Project assignedProject;

        private final String name;
        private Project project;

        public RecordingReference(String name) {
            this.name = name;
        }

        @Override
        public RecordingReference clone() {
            RecordingReference copy = new RecordingReference(name + "-copy");
            clonedFrom = this;
            return copy;
        }

        public void setProject(Project project) {
            this.project = project;
            assignedReference = this;
            assignedProject = project;
        }
    }
}
