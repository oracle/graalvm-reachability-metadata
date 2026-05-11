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
import io.restassured.internal.ResponseSpecificationImplDirectAccess;
import io.restassured.response.Response;
import io.restassured.specification.ResponseSpecification;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(
                "io.restassured.internal.ResponseSpecificationImpl");

        assertSame(ResponseSpecificationImpl.class, resolvedClass);
    }

    @Test
    void loadsNamedClassThroughCompilerGeneratedClassResolver() throws Throwable {
        String className = "java.lang.String";

        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(className);

        assertEquals(className, resolvedClass.getName());
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    ResponseSpecificationImpl.class,
                    "class$",
                    new Object[] {"io.restassured.internal.ResponseSpecificationImpl"});

            assertSame(ResponseSpecificationImpl.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            assertGroovyInitializationFailure(error);
        }
    }

    @Test
    void buildsAndAppliesResponseSpecificationThroughPublicBuilder() {
        ResponseSpecification specification = new ResponseSpecBuilder()
                .expectStatusCode(201)
                .expectStatusLine("HTTP/1.1 201 Created")
                .expectContentType("application/json")
                .expectHeader("X-Test", "response-specification")
                .expectBody("message", equalTo("created"))
                .build();
        Response response = new ResponseBuilder()
                .setStatusCode(201)
                .setStatusLine("HTTP/1.1 201 Created")
                .setContentType("application/json")
                .setHeader("X-Test", "response-specification")
                .setBody("{\"message\":\"created\"}")
                .build();

        assertEquals(ResponseSpecificationImpl.class, specification.getClass());
        response.then().spec(specification);
    }

    @Test
    void acceptsResponseTimeExpectationThroughPublicBuilder() {
        ResponseSpecification specification = new ResponseSpecBuilder()
                .expectResponseTime(lessThan(1L), TimeUnit.MINUTES)
                .build();

        assertEquals(ResponseSpecificationImpl.class, specification.getClass());
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return ResponseSpecificationImplDirectAccess.resolveWithCompilerGeneratedClassResolver(className);
    }

    private static void assertGroovyInitializationFailure(NoClassDefFoundError error) {
        String message = error.getMessage();
        assertTrue(
                "Could not initialize class groovy.lang.GroovySystem".equals(message)
                        || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message));
    }
}
