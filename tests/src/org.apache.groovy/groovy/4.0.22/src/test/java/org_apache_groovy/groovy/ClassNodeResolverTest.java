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
import org.codehaus.groovy.ast.decompiled.DecompiledClassNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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

    @Test
    void decompilesAClasspathClassToInspectItsMembers() throws Exception {
        CompilationUnit unit = new CompilationUnit();
        try {
            ClassNode model = new ClassNodeResolver().resolveName("groovy.namespace.QName", unit).getClassNode();
            assertInstanceOf(DecompiledClassNode.class, model);
            assertEquals("java.lang.String", model.getDeclaredField("localPart").getType().getName());
            assertEquals(3, model.getDeclaredConstructors().size());
            assertEquals("java.lang.String", model.getDeclaredMethods("getQualifiedName").get(0).getReturnType().getName());
        } finally {
            unit.getClassLoader().close();
        }
    }

}
