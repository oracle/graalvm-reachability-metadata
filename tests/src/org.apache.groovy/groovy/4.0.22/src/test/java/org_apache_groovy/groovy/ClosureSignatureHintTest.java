/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.transform.stc.SimpleType;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClosureSignatureHintTest {
    @Test
    void resolvesConfiguredClosureParameterTypesIncludingArrays() throws Exception {
        CompilationUnit unit = new CompilationUnit();
        try {
            SourceUnit source = unit.addSource("Hint.groovy", "class Hint {}");
            ClassNode[] types = new SimpleType().getParameterTypes(null,
                    new String[]{Payload.class.getName(), Payload.class.getName() + "[]"}, source, unit, source.getAST());
            assertEquals(Payload.class, types[0].getTypeClass());
            assertEquals(Payload[].class, types[1].getTypeClass());
        } finally {
            unit.getClassLoader().close();
        }
    }

    public static class Payload {
        public String name;
    }
}
