/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.ClassGeneratorUtil;
import bsh.Interpreter;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ClassGeneratorUtilTest {
    @Test
    void generatesClassWithScriptedConstructorAndMethod() throws Exception {
        Interpreter interpreter = new Interpreter();

        try {
            Object result = interpreter.eval("""
                    public class ClassGeneratorUtilScriptedException extends Exception {
                        public ClassGeneratorUtilScriptedException(String message) {
                            super(message);
                        }

                        public String scriptedMessage() {
                            return getMessage();
                        }
                    }

                    new ClassGeneratorUtilScriptedException("generated message").scriptedMessage();
                    """);

            assertThat(result).isEqualTo("generated message");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void startsInterpreterFromClassRelativeScriptResource() {
        assertThatCode(() -> ClassGeneratorUtil.startInterpreterForClass(ClassGeneratorUtilTest.class))
                .doesNotThrowAnyException();
    }
}
