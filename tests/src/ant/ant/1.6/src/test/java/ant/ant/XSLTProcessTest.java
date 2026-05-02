/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.XSLTLiaison;
import org.apache.tools.ant.taskdefs.XSLTProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class XSLTProcessTest {
    private static final String TRAX_LIAISON =
            "org.apache.tools.ant.taskdefs.optional.TraXLiaison";
    private static final String XSLP_LIAISON =
            "org.apache.tools.ant.taskdefs.optional.XslpLiaison";
    private static final String XALAN_LIAISON =
            "org.apache.tools.ant.taskdefs.optional.XalanLiaison";

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesDeprecatedXslpProcessorByName() {
        assertOptionalProcessorResolution("xslp", XSLP_LIAISON);
    }

    @Test
    void resolvesDeprecatedXalanProcessorByName() {
        assertOptionalProcessorResolution("xalan", XALAN_LIAISON);
    }

    @Test
    void resolvesCustomProcessorByClassName() {
        ExposedXSLTProcess task = newTask(TRAX_LIAISON);

        XSLTLiaison liaison = task.exposeLiaison();

        assertThat(liaison.getClass().getName()).isEqualTo(TRAX_LIAISON);
    }

    @Test
    void resolvesCustomProcessorThroughConfiguredAntClasspath() {
        ExposedXSLTProcess task = newTask(TRAX_LIAISON);
        task.createClasspath();

        XSLTLiaison liaison = task.exposeLiaison();

        assertThat(liaison.getClass().getName()).isEqualTo(TRAX_LIAISON);
    }

    private void assertOptionalProcessorResolution(String processor, String expectedClassName) {
        ExposedXSLTProcess task = newTask(processor);

        XSLTLiaison liaison = task.exposeLiaison();

        assertThat(liaison.getClass().getName()).isEqualTo(expectedClassName);
    }

    private ExposedXSLTProcess newTask(String processor) {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());
        ExposedXSLTProcess task = new ExposedXSLTProcess();
        task.setProject(project);
        task.setProcessor(processor);
        return task;
    }

    private static final class ExposedXSLTProcess extends XSLTProcess {
        XSLTLiaison exposeLiaison() {
            return getLiaison();
        }
    }
}
