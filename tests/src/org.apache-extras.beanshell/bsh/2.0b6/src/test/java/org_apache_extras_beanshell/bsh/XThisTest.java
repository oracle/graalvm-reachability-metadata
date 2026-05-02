/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.Interpreter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XThisTest {
    @Test
    void createsAndCachesInterfaceProxyForScriptedObject() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("""
                description(prefix) {
                    return prefix + " handled by script";
                }

                product(left, right) {
                    return left * right;
                }
                """);

        ScriptedService proxy = (ScriptedService) interpreter.getInterface(ScriptedService.class);

        assertThat(proxy.description("BeanShell")).isEqualTo("BeanShell handled by script");
        assertThat(proxy.product(6, 7)).isEqualTo(42);
        assertThat(proxy.equals(proxy)).isTrue();
        assertThat(proxy.equals(new Object())).isFalse();
        assertThat(proxy.toString()).contains("implements:", ScriptedService.class.getName());

        Object cachedProxy = interpreter.getInterface(ScriptedService.class);
        assertThat(cachedProxy).isSameAs(proxy);
    }

    public interface ScriptedService {
        String description(String prefix);

        int product(int left, int right);
    }
}
