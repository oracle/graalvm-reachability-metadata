/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.MacroDef;
import org.apache.tools.ant.taskdefs.MacroInstance;
import org.junit.jupiter.api.Test;

public class MacroDefTest {
    @Test
    void registersMacroInstanceComponentFromSequentialDefinition() {
        Project project = new Project();
        MacroDef macroDef = new MacroDef();
        macroDef.setProject(project);
        macroDef.setName("record-message");
        macroDef.createSequential();

        macroDef.execute();

        Class<?> componentClass = ComponentHelper.getComponentHelper(project)
                .getComponentClass("record-message");
        assertThat(componentClass).isSameAs(MacroInstance.class);
    }
}
