/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.RestAssuredResponseOptionsGroovyImplToStringClosure2DirectAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class RestAssuredResponseOptionsGroovyImplInner_toString_closure2Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RestAssuredResponseOptionsGroovyImpl"
            + Character.toString((char) 36)
            + "_toString_closure2";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        Class<?> resolvedClass = RestAssuredResponseOptionsGroovyImplToStringClosure2DirectAccess
                .resolveWithCompilerGeneratedClassResolver(String.class.getName());

        assertSame(String.class, resolvedClass);
    }

    @Test
    void compilerGeneratedClassResolverResolvesOwnClosureClass() throws Throwable {
        Class<?> resolvedClass = RestAssuredResponseOptionsGroovyImplToStringClosure2DirectAccess
                .resolveWithCompilerGeneratedClassResolver(CLOSURE_CLASS_NAME);

        assertEquals(CLOSURE_CLASS_NAME, resolvedClass.getName());
    }

}
