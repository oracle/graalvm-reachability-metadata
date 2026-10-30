/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_jsr223;

import groovy.lang.Binding;
import groovy.lang.Script;
import org.codehaus.groovy.jsr223.GroovyCompiledScript;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.codehaus.groovy.runtime.MethodClosure;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GroovyScriptEngineImplTest {
    @Test
    void invokesObjectMethodsThroughAnInterface() throws Exception {
        GroovyScriptEngineImpl engine = new GroovyScriptEngineImpl();
        try {
            GreetingService service = new GreetingService("Hello, ");
            Greeting greeting = engine.getInterface(service, Greeting.class);

            assertThat(greeting.greet("Ada")).isEqualTo("Hello, Ada");
            assertThat(greeting.greet("Grace")).isEqualTo("Hello, Grace");
        } finally {
            engine.getClassLoader().close();
        }
    }

    @Test
    void invokesBoundGlobalClosuresThroughAnInterface() throws Exception {
        GroovyScriptEngineImpl engine = new GroovyScriptEngineImpl();
        try {
            GreetingService service = new GreetingService("Welcome, ");
            engine.put("greet", new MethodClosure(service, "greet"));
            Greeting greeting = engine.getInterface(Greeting.class);

            assertThat(greeting.greet("Linus")).isEqualTo("Welcome, Linus");
            assertThat(greeting.greet("Margaret")).isEqualTo("Welcome, Margaret");
        } finally {
            engine.getClassLoader().close();
        }
    }

    @Test
    void evaluatesPrecompiledScriptsAndPublishesTheirMethodsAsGlobalFunctions() throws Exception {
        GroovyScriptEngineImpl engine = new GroovyScriptEngineImpl();
        try {
            engine.put("prefix", "Hello, ");
            engine.put("name", "Ada");
            GroovyCompiledScript script = new GroovyCompiledScript(engine, GreetingScript.class);

            assertThat(script.eval()).isEqualTo("Hello, Ada");
            assertThat(engine.invokeFunction("greet", "Grace")).isEqualTo("Hello, Grace");

            engine.put("prefix", "Welcome, ");
            engine.put("name", "Margaret");
            assertThat(script.eval()).isEqualTo("Welcome, Margaret");
            assertThat(engine.invokeFunction("greet", "Linus")).isEqualTo("Welcome, Linus");
        } finally {
            engine.getClassLoader().close();
        }
    }

    public static class GreetingScript extends Script {
        public GreetingScript(Binding binding) {
            super(binding);
        }

        @Override
        public Object run() {
            return greet((String) getBinding().getVariable("name"));
        }

        public String greet(String name) {
            return (String) getBinding().getVariable("prefix") + name;
        }
    }

    public interface Greeting {
        String greet(String name);
    }

    public static class GreetingService {
        private final String prefix;

        public GreetingService(String prefix) {
            this.prefix = prefix;
        }

        public String greet(String name) {
            return prefix + name;
        }
    }
}
