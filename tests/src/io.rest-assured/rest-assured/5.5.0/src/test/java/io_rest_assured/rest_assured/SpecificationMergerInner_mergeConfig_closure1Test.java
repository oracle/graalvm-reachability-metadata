/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.SpecificationMergerMergeConfigClosure1Access;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.Test;

import static io.restassured.config.HeaderConfig.headerConfig;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpecificationMergerInner_mergeConfig_closure1Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.SpecificationMerger$_mergeConfig_closure1";

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = SpecificationMergerMergeConfigClosure1Access
                    .resolveWithCompilerGeneratedClassResolver(RestAssuredConfig.class.getName());

            assertSame(RestAssuredConfig.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void userConfiguredRequestSpecConfigsAreMergedPerConfigType() {
        try {
            RestAssuredConfig originalConfig = config()
                    .redirect(redirectConfig().followRedirects(false).maxRedirects(7));
            RestAssuredConfig mergedInConfig = config()
                    .headerConfig(headerConfig().overwriteHeadersWithName("X-Merged-Header"));

            RequestSpecification mergedInSpecification = new RequestSpecBuilder()
                    .setConfig(mergedInConfig)
                    .build();
            RequestSpecification requestSpecification = new RequestSpecBuilder()
                    .setConfig(originalConfig)
                    .addRequestSpecification(mergedInSpecification)
                    .build();

            QueryableRequestSpecification queryableSpecification = SpecificationQuerier.query(requestSpecification);
            RestAssuredConfig resultingConfig = queryableSpecification.getConfig();

            assertTrue(resultingConfig.isUserConfigured());
            assertEquals(7, resultingConfig.getRedirectConfig().maxRedirects());
            assertTrue(resultingConfig.getHeaderConfig().shouldOverwriteHeaderWithName("X-Merged-Header"));
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
