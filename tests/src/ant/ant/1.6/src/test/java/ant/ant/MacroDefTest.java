/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.RuntimeConfigurable;
import org.apache.tools.ant.UnknownElement;
import org.apache.tools.ant.taskdefs.MacroDef;
import org.apache.tools.ant.taskdefs.MacroDef.Attribute;
import org.apache.tools.ant.taskdefs.MacroInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MacroDefTest {
    @TempDir
    File directory;

    @Test
    void definesAndInvokesParameterizedMacro() {
        Project project = TestSupport.project(directory);
        MacroDef definition = (MacroDef) project.createTask("macrodef");
        definition.setName("greet");
        Attribute attribute = new Attribute();
        attribute.setName("name");
        definition.addConfiguredAttribute(attribute);
        UnknownElement property = new UnknownElement("property");
        property.setProject(project);
        property.setNamespace("");
        property.setQName("property");
        property.setTaskName("property");
        property.setTaskType("property");
        RuntimeConfigurable configuration = new RuntimeConfigurable(property, "property");
        configuration.setAttribute("name", "macro.result");
        configuration.setAttribute("value", "Hello @{name}");
        definition.createSequential().addTask(property);
        definition.execute();

        MacroInstance invocation = (MacroInstance) project.createTask("greet");
        invocation.setDynamicAttribute("name", "Ant");
        invocation.execute();
        assertEquals("Hello Ant", project.getProperty("macro.result"));
    }
}
