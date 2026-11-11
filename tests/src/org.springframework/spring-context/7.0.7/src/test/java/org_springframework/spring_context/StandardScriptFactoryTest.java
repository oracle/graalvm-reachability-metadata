/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.springframework.scripting.support.StandardScriptFactory;
import org.springframework.scripting.support.StaticScriptSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class StandardScriptFactoryTest {
    @Test
    void instantiatesExistingClassReturnedByInterpretedScript() throws Exception {
        StandardScriptFactory factory = new StandardScriptFactory("beanshell", "inline:builder");
        StaticScriptSource source = new StaticScriptSource("java.lang.StringBuilder.class;");
        StringBuilder builder = assertInstanceOf(StringBuilder.class, factory.getScriptedObject(source));
        builder.append("interpreted");
        assertEquals("interpreted", builder.toString());
    }
}
