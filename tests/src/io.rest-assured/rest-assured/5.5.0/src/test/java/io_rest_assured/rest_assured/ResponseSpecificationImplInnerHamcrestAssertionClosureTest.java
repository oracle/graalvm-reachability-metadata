/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.ResponseBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.internal.ResponseSpecificationImpl;
import io.restassured.internal.ResponseSpecificationImpl.HamcrestAssertionClosure;
import io.restassured.internal.ResponseSpecificationImplInnerHamcrestAssertionClosureDirectAccess;
import io.restassured.response.Response;
import io.restassured.specification.ResponseSpecification;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInnerHamcrestAssertionClosureTest {
    @Test
    void resolvesClassThroughHamcrestAssertionClosureCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = ResponseSpecificationImplInnerHamcrestAssertionClosureDirectAccess
                .resolveWithCompilerGeneratedClassResolver(activeClosureClassName());

        assertSame(HamcrestAssertionClosure.class, resolvedClass);
    }

    @Test
    void resolvesRestAssuredClassOnlyKnownAtRuntimeThroughHamcrestAssertionClosureCompilerGeneratedClassResolver()
            throws Throwable {
        String className = String.join(".", "io", "restassured", "internal", "http", "CharsetExtractor");

        Class<?> resolvedClass = ResponseSpecificationImplInnerHamcrestAssertionClosureDirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void reportsMissingClassThroughHamcrestAssertionClosureCompilerGeneratedClassResolver() {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> ResponseSpecificationImplInnerHamcrestAssertionClosureDirectAccess
                        .resolveWithCompilerGeneratedClassResolver(activeClosureClassName() + "Missing"));

        assertTrue(error.getMessage().endsWith("HamcrestAssertionClosureMissing"));
    }

    @Test
    void resolvesClassThroughGroovyStaticDispatchOnHamcrestAssertionClosure() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    HamcrestAssertionClosure.class,
                    "class$",
                    new Object[] {activeClosureClassName()});

            assertSame(HamcrestAssertionClosure.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            assertGroovyInitializationFailure(error);
        }
    }

    @Test
    void validatesResponseExpectationsThroughHamcrestAssertionClosure() {
        ResponseSpecification specification = new ResponseSpecBuilder()
                .expectStatusCode(202)
                .expectStatusLine("HTTP/1.1 202 Accepted")
                .expectContentType("application/json")
                .expectHeader("X-Hamcrest-Closure", "validated")
                .expectBody("status", equalTo("accepted"))
                .build();
        Response response = new ResponseBuilder()
                .setStatusCode(202)
                .setStatusLine("HTTP/1.1 202 Accepted")
                .setContentType("application/json")
                .setHeader("X-Hamcrest-Closure", "validated")
                .setBody("{\"status\":\"accepted\"}")
                .build();

        response.then().spec(specification);
    }

    private static String activeClosureClassName() {
        return ResponseSpecificationImpl.class.getName()
                + Character.toString((char) 36)
                + HamcrestAssertionClosure.class.getSimpleName();
    }

    private static void assertGroovyInitializationFailure(NoClassDefFoundError error) {
        String message = error.getMessage();
        assertTrue(
                "Could not initialize class groovy.lang.GroovySystem".equals(message)
                        || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message));
    }
}
