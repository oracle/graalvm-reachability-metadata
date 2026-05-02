/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.UnknownElement;
import org.apache.tools.ant.taskdefs.Antlib;
import org.apache.tools.ant.taskdefs.AntlibDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AntlibTest {
    @Test
    void executesConfiguredAntlibDefinitionWithDefaultAntlibClassLoader() {
        Project project = new Project();
        project.init();

        RecordingAntlibDefinition definition = new RecordingAntlibDefinition();
        Antlib antlib = new Antlib();
        antlib.setProject(project);
        antlib.init();
        antlib.addTask(new PreconfiguredUnknownElement(definition));

        antlib.execute();

        assertThat(definition.initialized).isTrue();
        assertThat(definition.executed).isTrue();
        assertThat(definition.getURI()).isEmpty();
        assertThat(definition.getAntlibClassLoader()).isSameAs(Antlib.class.getClassLoader());
    }

    private static final class PreconfiguredUnknownElement extends UnknownElement {
        private final Task realThing;

        private PreconfiguredUnknownElement(Task realThing) {
            super("recording");
            this.realThing = realThing;
        }

        @Override
        public void maybeConfigure() {
            // The Antlib task only needs the already-created nested definition.
        }

        @Override
        public Object getRealThing() {
            return realThing;
        }
    }

    private static final class RecordingAntlibDefinition extends AntlibDefinition {
        private boolean initialized;
        private boolean executed;

        @Override
        public void init() {
            initialized = true;
            super.init();
        }

        @Override
        public void execute() {
            executed = true;
        }
    }
}
