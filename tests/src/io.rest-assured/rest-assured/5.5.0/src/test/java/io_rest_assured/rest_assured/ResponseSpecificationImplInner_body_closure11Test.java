/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.ResponseSpecificationImplBodyClosure11DirectAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInner_body_closure11Test {
    @Test
    void resolvesBodyClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = activeClosureClassName();

        Class<?> resolvedClass = ResponseSpecificationImplBodyClosure11DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesRuntimeJdkClassThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = String.join(".", "java", "util", "LinkedHashMap");

        Class<?> resolvedClass = ResponseSpecificationImplBodyClosure11DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> ResponseSpecificationImplBodyClosure11DirectAccess
                        .resolveWithCompilerGeneratedClassResolver(activeClosureClassName() + "Missing"));

        assertTrue(error.getMessage().endsWith("closure11Missing"));
    }

    private static String activeClosureClassName() {
        return "io.restassured.internal.ResponseSpecificationImpl"
                + Character.toString((char) 36)
                + "_body_closure11";
    }
}
