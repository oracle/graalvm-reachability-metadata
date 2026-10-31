/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassNode;
import groovy.lang.Reference;
import org.codehaus.groovy.control.ClassNodeResolver;
import org.codehaus.groovy.control.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ClassNodeResolverTest {
    @Test
    void resolvesAndCachesAClasspathClassModel() throws Exception {
        CompilationUnit unit = new CompilationUnit();
        try {
            ClassNodeResolver resolver = new ClassNodeResolver();
            ClassNode model = resolver.resolveName(Reference.class.getName(), unit).getClassNode();
            assertEquals(Reference.class.getName(), model.getName());
            assertEquals("java.lang.Object", model.getDeclaredField("value").getType().getName());
            assertSame(model, resolver.resolveName(Reference.class.getName(), unit).getClassNode());
        } finally {
            unit.getClassLoader().close();
        }
    }

}
