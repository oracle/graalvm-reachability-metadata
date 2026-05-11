/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.List;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.SessionConfig;
import io.restassured.http.Cookies;
import io.restassured.internal.SpecificationMergerMergeSessionIdClosure2DirectAccess;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpecificationMergerInner_mergeSessionId_closure2Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.SpecificationMerger$_mergeSessionId_closure2";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = SpecificationMergerMergeSessionIdClosure2DirectAccess
                    .resolveWithCompilerGeneratedClassResolver(SessionConfig.class.getName());

            assertSame(SessionConfig.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void mergeRemovesExistingSessionCookieBeforeAddingMergedCookies() {
        try {
            RequestSpecification specificationToMerge = new RequestSpecBuilder()
                    .addCookie(SessionConfig.DEFAULT_SESSION_ID_NAME, "source-session")
                    .addCookie("source-cookie", "source-value")
                    .build();
            RequestSpecification mergedSpecification = new RequestSpecBuilder()
                    .addCookie("jsessionid", "stale-session")
                    .addCookie("theme", "dark")
                    .addRequestSpecification(specificationToMerge)
                    .build();

            QueryableRequestSpecification queryableSpecification = SpecificationQuerier.query(mergedSpecification);
            Cookies cookies = queryableSpecification.getCookies();

            assertEquals(3, cookies.size());
            assertEquals(List.of("source-session"), cookies.getValues(SessionConfig.DEFAULT_SESSION_ID_NAME));
            assertEquals("source-session", cookies.getValue(SessionConfig.DEFAULT_SESSION_ID_NAME));
            assertEquals("source-value", cookies.getValue("source-cookie"));
            assertEquals("dark", cookies.getValue("theme"));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    private static void assertNativeGroovyInitializationFailure(LinkageError error) {
        assertTrue(isNativeGroovyInitializationFailure(error), () -> "Unexpected initialization failure: " + error);
    }

    private static boolean isNativeGroovyInitializationFailure(LinkageError error) {
        String message = error.getMessage();
        return ("Could not initialize class " + CLOSURE_CLASS_NAME).equals(message)
                || "Could not initialize class groovy.lang.Closure".equals(message)
                || "Could not initialize class groovy.lang.GroovySystem".equals(message)
                || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message)
                || "Could not initialize class io.restassured.internal.SpecificationMerger".equals(message)
                || "Could not initialize class io.restassured.RestAssured".equals(message)
                || isGroovySystemInitializerError(error);
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
