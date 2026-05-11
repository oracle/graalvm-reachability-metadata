/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import groovy.lang.Closure;
import io.restassured.internal.RestAssuredHttpBuilderGroovyHelper;
import io.restassured.internal.RestAssuredHttpBuilderGroovyHelperDirectAccess;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestAssuredHttpBuilderGroovyHelperTest {
    @Test
    void flattensNestedCollectionsToStrings() {
        Collection<String> flattened = RestAssuredHttpBuilderGroovyHelper.flattenToString(
                Arrays.asList("alpha", Arrays.asList(1, null, "omega")));

        assertEquals(Arrays.asList("alpha", "1", null, "omega"), new ArrayList<>(flattened));
    }

    @Test
    void createdClosureDelegatesToAssertionClosure() {
        Closure<?> assertionClosure = new Closure<Object>(this) {
            public Object doCall(Object response, Object content) {
                return response + ":" + content;
            }
        };

        Closure<?> adapter = RestAssuredHttpBuilderGroovyHelper.createClosureThatCalls(assertionClosure);

        assertEquals("response:body", adapter.call("response", "body"));
    }

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RestAssuredHttpBuilderGroovyHelperDirectAccess
                    .resolveWithCompilerGeneratedClassResolver(RestAssuredHttpBuilderGroovyHelper.class.getName());

            assertSame(RestAssuredHttpBuilderGroovyHelper.class, resolvedClass);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverExercisesClassNotFoundBranch() throws Throwable {
        String missingClassName = "io.restassured.internal.RestAssuredHttpBuilderGroovyHelperMissingTarget";
        try {
            NoClassDefFoundError error = assertThrows(
                    NoClassDefFoundError.class,
                    () -> RestAssuredHttpBuilderGroovyHelperDirectAccess.resolveWithCompilerGeneratedClassResolver(
                            missingClassName));

            assertTrue(error.getMessage().contains(missingClassName));
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Throwable throwable) throws Throwable {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        throw throwable;
    }
}
