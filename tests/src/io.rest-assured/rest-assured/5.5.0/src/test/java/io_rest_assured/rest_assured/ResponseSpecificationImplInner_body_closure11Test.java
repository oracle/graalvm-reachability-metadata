/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.restassured.internal.assertion.BodyMatcher;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResponseSpecificationImplInner_body_closure11Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_body_closure11";

    @Test
    void generatedBodyClosureResolvesBodyMatcherClass() throws Throwable {
        try {
            assertEquals(BodyMatcher.class, invokeGeneratedClassLookup(BodyMatcher.class.getName()));
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Error error
                    && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            throw exception;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = ResponseSpecificationImplInner_body_closure11Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        Method classHelper = closureClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }
}
