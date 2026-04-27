/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectComponent;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.selectors.ExtendFileSelector;
import org.apache.tools.ant.types.selectors.ExtendSelector;
import org.apache.tools.ant.types.selectors.FileSelector;
import org.junit.jupiter.api.Test;

public class ExtendSelectorTest {
    @Test
    void createsSelectorByNameWithoutConfiguredClasspath() {
        Project project = new Project();
        ExtendSelector selector = new ExtendSelector();
        Parameter parameter = parameter("mode", "include");
        RecordingExtendFileSelector.lastParameters = null;
        RecordingExtendFileSelector.lastProject = null;
        selector.setProject(project);
        selector.setClassname(RecordingExtendFileSelector.class.getName());
        selector.addParam(parameter);

        boolean selected = selector.isSelected(new File("."), "selected.txt", new File("selected.txt"));

        assertThat(selected).isTrue();
        assertThat(RecordingExtendFileSelector.lastParameters).containsExactly(parameter);
        assertThat(RecordingExtendFileSelector.lastProject).isSameAs(project);
    }

    @Test
    void createsSelectorByNameWithConfiguredClasspath() {
        RecordingProject project = new RecordingProject();
        ExtendSelector selector = new ExtendSelector();
        Path classpath = new Path(project);
        RecordingFileSelector.lastProject = null;
        selector.setProject(project);
        selector.setClassname(RecordingFileSelector.class.getName());
        selector.setClasspath(classpath);

        boolean selected = selector.isSelected(new File("."), "rejected.txt", new File("rejected.txt"));

        assertThat(selected).isFalse();
        assertThat(project.requestedClasspath).isSameAs(classpath);
        assertThat(RecordingFileSelector.lastProject).isSameAs(project);
    }

    private static Parameter parameter(String name, String value) {
        Parameter parameter = new Parameter();
        parameter.setName(name);
        parameter.setValue(value);
        return parameter;
    }

    public static final class RecordingExtendFileSelector extends ProjectComponent implements ExtendFileSelector {
        private static Parameter[] lastParameters;
        private static Project lastProject;

        public RecordingExtendFileSelector() {
        }

        @Override
        public void setProject(Project project) {
            super.setProject(project);
            lastProject = project;
        }

        @Override
        public void setParameters(Parameter[] parameters) {
            lastParameters = parameters;
        }

        @Override
        public boolean isSelected(File basedir, String filename, File file) {
            return true;
        }
    }

    public static final class RecordingFileSelector extends ProjectComponent implements FileSelector {
        private static Project lastProject;

        public RecordingFileSelector() {
        }

        @Override
        public void setProject(Project project) {
            super.setProject(project);
            lastProject = project;
        }

        @Override
        public boolean isSelected(File basedir, String filename, File file) {
            return false;
        }
    }

    private static final class RecordingProject extends Project {
        private Path requestedClasspath;

        @Override
        public AntClassLoader createClassLoader(Path path) {
            requestedClasspath = path;
            return new AntClassLoader(ExtendSelectorTest.class.getClassLoader(), true);
        }
    }
}
