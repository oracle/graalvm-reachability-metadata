/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.File;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.apache.tools.ant.types.Path;

public class ConfigurationBean {
    String text;
    String label;
    char initial;
    boolean enabled;
    Class<?> type;
    File file;
    Path path;
    Mode mode;
    Integer count;
    Value created;
    Value configured;
    ProjectValue projectConfigured;
    Value added;
    ProjectValue projectAdded;

    public void addText(String value) {
        text = value;
    }

    public void setLabel(String value) {
        label = value;
    }

    public void setInitial(char value) {
        initial = value;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public void setType(Class<?> value) {
        type = value;
    }

    public void setFile(File value) {
        file = value;
    }

    public void setPath(Path value) {
        path = value;
    }

    public void setMode(Mode value) {
        mode = value;
    }

    public void setCount(Integer value) {
        count = value;
    }

    public Value createCreated() {
        created = new Value();
        return created;
    }

    public void addConfiguredConfigured(Value value) {
        configured = value;
    }

    public void addConfiguredProjectconfigured(ProjectValue value) {
        projectConfigured = value;
    }

    public void addAdded(Value value) {
        added = value;
    }

    public void addProjectadded(ProjectValue value) {
        projectAdded = value;
    }

    public static class Value {
        String label;

        public void setLabel(String value) {
            label = value;
        }
    }

    public static class ProjectValue extends Value {
        final Project project;

        public ProjectValue(Project project) {
            this.project = project;
        }
    }

    public static class Mode extends EnumeratedAttribute {
        @Override
        public String[] getValues() {
            return new String[] {"fast", "careful"};
        }
    }
}
