/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.restassured.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ResponseSpecificationImplHamcrestAssertionClosureValidateHeadersAndCookiesClosure6Access {
    private ResponseSpecificationImplHamcrestAssertionClosureValidateHeadersAndCookiesClosure6Access() {
    }

    public static Class<?> resolveWithGeneratedDirectAccess(String className) throws Throwable {
        return ResponseSpecificationImplHamcrestAssertionClosureValidateHeadersAndCookiesClosure6DirectAccess
                .resolveWithCompilerGeneratedClassResolver(className);
    }

    public static Class<?> resolveWithJavaReflectionDispatch(String className) throws Throwable {
        Method classResolver = activeClosureClass().getDeclaredMethod("class$", String.class);
        classResolver.setAccessible(true);
        try {
            return (Class<?>) classResolver.invoke(null, className);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    public static Class<?> activeClosureClass() {
        return ResponseSpecificationImpl$_HamcrestAssertionClosure_validateHeadersAndCookies_closure6.class;
    }
}
