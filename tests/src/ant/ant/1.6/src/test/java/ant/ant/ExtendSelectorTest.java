/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.selectors.ExtendSelector;
import org.apache.tools.ant.types.selectors.FilenameSelector;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtendSelectorTest {
    @TempDir
    File temporaryDirectory;

    @Test
    void createsSelectorByClassNameAndDelegatesSelection() throws IOException {
        File selectedFile = createFile("input.txt");
        ExtendSelector selector = filenameSelector(newProject());

        boolean selected = selector.isSelected(
                temporaryDirectory,
                selectedFile.getName(),
                selectedFile);

        assertThat(selected).isTrue();
    }

    @Test
    void createsSelectorWithConfiguredClasspathAndDelegatesSelection() throws IOException {
        File selectedFile = createFile("from-classpath.txt");
        Project project = newProject();
        ExtendSelector selector = filenameSelector(project);
        selector.setClasspath(runtimeClasspath(project));

        try {
            boolean selected = selector.isSelected(
                    temporaryDirectory,
                    selectedFile.getName(),
                    selectedFile);

            assertThat(selected).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private Project newProject() {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory);
        return project;
    }

    private ExtendSelector filenameSelector(Project project) {
        ExtendSelector selector = new ExtendSelector();
        selector.setProject(project);
        selector.setClassname(FilenameSelector.class.getName());
        selector.addParam(parameter(FilenameSelector.NAME_KEY, "*.txt"));
        return selector;
    }

    private Path runtimeClasspath(Project project) {
        String classpath = System.getProperty("java.class.path");
        assertThat(classpath).isNotBlank();
        return new Path(project, classpath);
    }

    private static Parameter parameter(String name, String value) {
        Parameter parameter = new Parameter();
        parameter.setName(name);
        parameter.setValue(value);
        return parameter;
    }

    private File createFile(String name) throws IOException {
        File file = new File(temporaryDirectory, name);
        assertThat(file.createNewFile()).isTrue();
        return file;
    }
}
