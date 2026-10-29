/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.File;
import java.nio.file.Files;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Ant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AntTest {
    @TempDir
    File directory;

    @Test
    void clonesAndRebindsReferenceForNestedBuild() throws Exception {
        File build = new File(directory, "child.xml");
        Files.writeString(build.toPath(), """
                <project><target name="verify">
                  <taskdef name="inspect" classname="ant.ant.AntTest$InspectReference"/>
                  <inspect/>
                </target></project>
                """);
        Project project = TestSupport.project(directory);
        SharedReference original = new SharedReference();
        original.value = "shared input";
        original.setProject(project);
        project.addReference("original", original);
        Ant ant = new Ant();
        ant.setProject(project);
        ant.init();
        ant.setDir(directory);
        ant.setAntfile("child.xml");
        ant.setTarget("verify");
        Ant.Reference reference = new Ant.Reference();
        reference.setRefId("original");
        reference.setToRefid("copied");
        ant.addReference(reference);
        InspectReference.received = null;
        ant.execute();
        assertEquals("shared input", InspectReference.received.value);
        assertNotSame(original, InspectReference.received);
        assertNotSame(project, InspectReference.received.project);
    }

    public static class SharedReference implements Cloneable {
        private String value;
        private Project project;

        public void setProject(Project project) {
            this.project = project;
        }

        @Override
        public SharedReference clone() throws CloneNotSupportedException {
            return (SharedReference) super.clone();
        }
    }

    public static class InspectReference extends Task {
        static SharedReference received;

        @Override
        public void execute() {
            received = (SharedReference) getProject().getReference("copied");
            assertEquals(getProject(), received.project);
        }
    }
}
