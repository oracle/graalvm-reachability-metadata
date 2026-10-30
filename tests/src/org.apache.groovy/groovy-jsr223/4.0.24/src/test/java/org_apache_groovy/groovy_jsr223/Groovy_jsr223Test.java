/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_jsr223;

import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.junit.jupiter.api.Test;
import org_apache_groovy.groovy_jsr223.GroovyScriptEngineImplTest.GreetingService;

import static org.assertj.core.api.Assertions.assertThat;

public class Groovy_jsr223Test {
    @Test
    void factoryCreatesAnEngineThatInvokesObjectMethods() throws Exception {
        GroovyScriptEngineFactory factory = new GroovyScriptEngineFactory();
        GroovyScriptEngineImpl engine = (GroovyScriptEngineImpl) factory.getScriptEngine();
        try {
            GreetingService service = new GreetingService("Good evening, ");

            assertThat(engine.getFactory()).isSameAs(factory);
            assertThat(engine.invokeMethod(service, "greet", "Ada")).isEqualTo("Good evening, Ada");
            assertThat(engine.invokeMethod(service, "greet", "Grace")).isEqualTo("Good evening, Grace");
        } finally {
            engine.getClassLoader().close();
        }
    }
}
