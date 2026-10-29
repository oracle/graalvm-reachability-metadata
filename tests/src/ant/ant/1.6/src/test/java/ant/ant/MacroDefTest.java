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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MacroDefTest {
    @TempDir
    File directory;

    @Test
    void definesAndInvokesParameterizedMacro() throws Exception {
        Project project = TestSupport.build(directory, """
                <project><target name="verify">
                  <macrodef name="greet">
                    <attribute name="name"/>
                    <sequential><property name="macro.result" value="Hello @{name}"/></sequential>
                  </macrodef>
                  <greet name="Ant"/>
                </target></project>
                """);
        assertEquals("Hello Ant", project.getProperty("macro.result"));
    }
}
