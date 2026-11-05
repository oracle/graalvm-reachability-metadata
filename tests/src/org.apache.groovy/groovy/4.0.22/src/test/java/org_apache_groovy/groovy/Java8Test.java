/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Java8Test {
    @Test
    void modelsJavaFieldsMethodsAndConstructorsAsGroovyAstNodes() {
        ClassNode node = ClassHelper.makeWithoutCaching(Person.class);
        assertEquals(String.class, node.getField("name").getType().getTypeClass());
        assertEquals(1, node.getDeclaredConstructors().size());
        assertTrue(node.getMethods("greeting").stream().anyMatch(method -> method.getParameters().length == 0));
    }

    public static class Person {
        public final String name;

        public Person(String name) {
            this.name = name;
        }

        public String greeting() {
            return "Hello " + name;
        }
    }
}
