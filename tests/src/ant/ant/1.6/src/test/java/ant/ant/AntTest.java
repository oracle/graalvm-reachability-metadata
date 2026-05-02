/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Ant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class AntTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesConfiguredReferenceByCloningAndAssigningChildProject() throws IOException {
        Path buildFile = temporaryDirectory.resolve("build.xml");
        Files.writeString(buildFile, """
                <project name="child" default="noop">
                    <target name="noop"/>
                </project>
                """, StandardCharsets.UTF_8);

        Project parentProject = new Project();
        parentProject.init();
        parentProject.setBaseDir(temporaryDirectory.toFile());

        CloneableReference originalReference = new CloneableReference();
        parentProject.addReference("original", originalReference);

        Ant antTask = new Ant();
        antTask.setProject(parentProject);
        antTask.setTaskName("ant");
        antTask.setDir(temporaryDirectory.toFile());
        antTask.setAntfile(buildFile.getFileName().toString());
        antTask.setTarget("noop");
        antTask.addReference(reference("original", "copied"));

        antTask.execute();

        assertThat(originalReference.cloneCount).isEqualTo(1);
        assertThat(originalReference.lastClone).isNotNull();
        assertThat(originalReference.lastClone).isNotSameAs(originalReference);
        assertThat(originalReference.lastClone.assignedProject).isNotNull();
        assertThat(originalReference.assignedProject).isNull();
    }

    private static Ant.Reference reference(String refid, String toRefid) {
        Ant.Reference reference = new Ant.Reference();
        reference.setRefId(refid);
        reference.setToRefid(toRefid);
        return reference;
    }

    public static class CloneableReference implements Cloneable {
        private int cloneCount;
        private CloneableReference lastClone;
        private Project assignedProject;

        @Override
        public Object clone() {
            CloneableReference copy = new CloneableReference();
            cloneCount++;
            lastClone = copy;
            return copy;
        }

        public void setProject(Project project) {
            assignedProject = project;
        }
    }
}
