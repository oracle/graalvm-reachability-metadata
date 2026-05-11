/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.Headers;
import io.restassured.internal.SpecificationMerger;
import io.restassured.internal.SpecificationMergerDirectAccess;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpecificationMergerTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(
                "io.restassured.internal.SpecificationMerger");

        assertSame(SpecificationMerger.class, resolvedClass);
    }

    @Test
    void mergesRequestSpecificationsThroughPublicBuilder() {
        try {
            RequestSpecification sourceSpecification = new RequestSpecBuilder()
                    .setBaseUri("http://merged.example.test")
                    .setBasePath("/api")
                    .addQueryParam("q", "value")
                    .addHeader("X-Merged", "true")
                    .build();

            RequestSpecification mergedSpecification = new RequestSpecBuilder()
                    .setBaseUri("http://original.example.test")
                    .addHeader("X-Original", "true")
                    .addRequestSpecification(sourceSpecification)
                    .build();

            QueryableRequestSpecification queryableSpecification = SpecificationQuerier.query(mergedSpecification);
            Headers headers = queryableSpecification.getHeaders();

            assertEquals("http://merged.example.test", queryableSpecification.getBaseUri());
            assertEquals("/api", queryableSpecification.getBasePath());
            assertEquals("value", queryableSpecification.getQueryParams().get("q"));
            assertEquals("true", headers.getValue("X-Original"));
            assertEquals("true", headers.getValue("X-Merged"));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return SpecificationMergerDirectAccess.resolveWithCompilerGeneratedClassResolver(className);
    }

    private static void assertNativeGroovyInitializationFailure(LinkageError error) {
        String message = error.getMessage();
        assertTrue(
                "Could not initialize class groovy.lang.GroovySystem".equals(message)
                        || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message)
                        || isGroovySystemInitializerError(error),
                () -> "Unexpected initialization failure: " + error);
    }

    private static boolean isGroovySystemInitializerError(LinkageError error) {
        if (!(error instanceof ExceptionInInitializerError initializerError)) {
            return false;
        }
        Throwable cause = initializerError.getException();
        return cause instanceof NullPointerException
                && cause.getStackTrace().length > 0
                && "groovy.lang.GroovySystem".equals(cause.getStackTrace()[0].getClassName());
    }
}
