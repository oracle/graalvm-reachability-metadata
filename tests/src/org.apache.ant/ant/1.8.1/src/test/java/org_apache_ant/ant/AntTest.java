/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.taskdefs.Ant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AntTest {
    @Test
    void copiesCloneableReferencesToSubprojects(@TempDir Path temporaryDirectory) throws IOException {
        Path buildFile = temporaryDirectory.resolve("build.xml");
        Files.writeString(buildFile, """
                <project default="noop">
                    <target name="noop"/>
                </project>
                """);

        String propertyName = ProjectHelper.HELPER_PROPERTY;
        String previousHelper = System.getProperty(propertyName);
        System.setProperty(propertyName, ProjectHelperRepositoryTest.LoadedProjectHelper.class.getName());
        try {
            Project parentProject = new Project();
            parentProject.initProperties();
            parentProject.setBaseDir(temporaryDirectory.toFile());
            CloneableReference reference = new CloneableReference();
            parentProject.addReference("source", reference);

            Ant ant = new Ant();
            ant.setProject(parentProject);
            ant.setAntfile(buildFile.getFileName().toString());
            ant.setTarget("noop");
            Ant.Reference copiedReference = new Ant.Reference();
            copiedReference.setRefId("source");
            copiedReference.setToRefid("copy");
            ant.addReference(copiedReference);

            ant.execute();

            assertThat(reference.copy).isNotNull().isNotSameAs(reference);
            assertThat(reference.copy.project).isNotNull().isNotSameAs(parentProject);
        } finally {
            if (previousHelper == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousHelper);
            }
        }
    }

    public static class CloneableReference {
        private CloneableReference copy;
        private Project project;

        public CloneableReference clone() {
            copy = new CloneableReference();
            return copy;
        }

        public void setProject(Project project) {
            this.project = project;
        }
    }
}
