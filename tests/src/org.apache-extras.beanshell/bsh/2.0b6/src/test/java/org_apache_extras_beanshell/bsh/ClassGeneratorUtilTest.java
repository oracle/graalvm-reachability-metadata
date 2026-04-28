/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.Capabilities;
import bsh.ClassGeneratorUtil;
import bsh.Interpreter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassGeneratorUtilTest {

    @AfterEach
    public void disableAccessibilityAfterTest() throws Exception {
        Capabilities.setAccessibility(false);
    }

    @Test
    public void scriptedSubclassGenerationInspectsSuperclassConstructorsAndOverriddenMethods() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("""
                class GeneratedArrayListCoverage extends java.util.ArrayList {
                    public GeneratedArrayListCoverage() {
                        super(2);
                    }

                    public int size() {
                        return super.size() + 10;
                    }
                }

                list = new GeneratedArrayListCoverage();
                list.add("alpha");
                list.add("beta");
                return list.size();
                """);

        assertThat(result).isEqualTo(12);
    }

    @Test
    public void savedClassBootstrapLoadsCompanionScriptResource() {
        ClassGeneratorUtil.startInterpreterForClass(ClassGeneratorUtilTest.class);
    }
}
