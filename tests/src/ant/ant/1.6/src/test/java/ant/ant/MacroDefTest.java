/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.MacroDef;
import org.apache.tools.ant.taskdefs.MacroInstance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MacroDefTest {
    @Test
    void executeRegistersMacroInstanceDefinition() {
        Project project = new Project();
        MacroDef macroDef = new MacroDef();
        macroDef.setProject(project);
        macroDef.setName("sample-macro");
        macroDef.createSequential();

        macroDef.execute();

        ComponentHelper componentHelper = ComponentHelper.getComponentHelper(project);
        AntTypeDefinition definition = componentHelper.getDefinition("sample-macro");

        assertThat(definition).isNotNull();
        assertThat(definition.getTypeClass(project)).isSameAs(MacroInstance.class);
        assertThat(definition.getExposedClass(project)).isSameAs(MacroInstance.class);
    }
}
