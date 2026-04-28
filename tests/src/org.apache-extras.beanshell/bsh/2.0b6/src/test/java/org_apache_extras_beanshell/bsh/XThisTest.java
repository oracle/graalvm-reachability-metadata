/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.Interpreter;
import bsh.This;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XThisTest {

    private static final String RUN_PROPERTY = "beanshell.xthis.runnable.called";

    @AfterEach
    public void clearRunProperty() {
        System.clearProperty(RUN_PROPERTY);
    }

    @Test
    public void scriptedThisCanBeExposedAsRunnableProxy() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("""
                run() {
                    java.lang.System.setProperty("%s", "true");
                }
                """.formatted(RUN_PROPERTY));
        This scriptedThis = interpreter.getNameSpace().getThis(interpreter);

        Object scriptedObject = scriptedThis.getInterface(Runnable.class);

        assertThat(scriptedObject).isInstanceOf(Runnable.class);
        Runnable scriptedRunnable = (Runnable) scriptedObject;
        scriptedRunnable.run();

        assertThat(System.getProperty(RUN_PROPERTY)).isEqualTo("true");
    }
}
