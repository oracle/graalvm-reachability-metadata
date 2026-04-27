/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.UnknownElement;
import org.apache.tools.ant.taskdefs.Antlib;
import org.apache.tools.ant.taskdefs.AntlibDefinition;
import org.junit.jupiter.api.Test;

public class AntlibTest {
    @Test
    void appliesDefaultAntlibClassLoaderToNestedDefinitions() {
        Project project = new Project();
        project.init();
        Antlib antlib = new Antlib();
        antlib.setProject(project);
        antlib.init();
        RecordingAntlibDefinition definition = new RecordingAntlibDefinition();
        definition.setProject(project);
        definition.setTaskName("recording-definition");
        antlib.addTask(new ConfiguredUnknownElement("recording-definition", definition));

        antlib.execute();

        assertThat(definition.executed).isTrue();
        assertThat(definition.getURI()).isEmpty();
        assertThat(definition.getAntlibClassLoader()).isSameAs(Antlib.class.getClassLoader());
    }

    private static final class RecordingAntlibDefinition extends AntlibDefinition {
        private boolean executed;

        @Override
        public void execute() {
            executed = true;
        }
    }

    private static final class ConfiguredUnknownElement extends UnknownElement {
        private final Object configuredObject;

        private ConfiguredUnknownElement(String elementName, Object configuredObject) {
            super(elementName);
            this.configuredObject = configuredObject;
        }

        @Override
        public void maybeConfigure() {
        }

        @Override
        public Object getRealThing() {
            return configuredObject;
        }
    }
}
