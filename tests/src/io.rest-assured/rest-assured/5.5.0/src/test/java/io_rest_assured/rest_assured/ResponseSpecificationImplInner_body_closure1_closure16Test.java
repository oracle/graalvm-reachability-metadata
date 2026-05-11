/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.internal.ResponseSpecificationImplBodyClosure1Closure16DirectAccess;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInner_body_closure1_closure16Test {
    @Test
    void resolvesActiveClosureThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = activeClosureClassName();

        Class<?> resolvedClass = ResponseSpecificationImplBodyClosure1Closure16DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesRuntimeJdkClassThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = String.join(".", "java", "util", "LinkedList");

        Class<?> resolvedClass = ResponseSpecificationImplBodyClosure1Closure16DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void reportsMissingClassThroughCompilerGeneratedClassResolver() {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> ResponseSpecificationImplBodyClosure1Closure16DirectAccess
                        .resolveWithCompilerGeneratedClassResolver(activeClosureClassName() + "Missing"));

        assertTrue(error.getMessage().endsWith("closure16Missing"));
    }

    @Test
    void validatesAdditionalWholeBodyMatchersThroughPublicResponseSpecification() {
        Response response = new ResponseBuilder()
                .setBody("dynamic coverage for rest-assured")
                .build();

        response.then()
                .body(containsString("dynamic"), containsString("rest-assured"));
    }

    private static String activeClosureClassName() {
        return "io.restassured.internal.ResponseSpecificationImpl"
                + Character.toString((char) 36)
                + "_body_closure1_closure16";
    }
}
