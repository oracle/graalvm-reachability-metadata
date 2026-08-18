/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import org.springframework.scripting.ScriptSource;
import org.springframework.scripting.support.StandardScriptFactory;
import org.springframework.scripting.support.StaticScriptSource;

public class StandardScriptFactoryTest {

    @Test
    void scriptedClassResultIsInstantiatedWithDefaultConstructor() throws Exception {
        StandardScriptFactory factory = new ClassReturningStandardScriptFactory(DefaultConstructedScript.class);
        ScriptSource scriptSource = new StaticScriptSource("script that evaluates to a class");

        Object scriptedObject = factory.getScriptedObject(scriptSource);

        DefaultConstructedScript script = assertInstanceOf(DefaultConstructedScript.class, scriptedObject);
        assertEquals("constructed", script.message());
    }

    private static final class ClassReturningStandardScriptFactory extends StandardScriptFactory {

        private final Class<?> scriptClass;

        private ClassReturningStandardScriptFactory(Class<?> scriptClass) {
            super("testScriptSource");
            this.scriptClass = scriptClass;
        }

        @Override
        protected Object evaluateScript(ScriptSource scriptSource) {
            return this.scriptClass;
        }
    }

    public static final class DefaultConstructedScript {

        private final String message;

        public DefaultConstructedScript() {
            this.message = "constructed";
        }

        String message() {
            return this.message;
        }
    }
}
