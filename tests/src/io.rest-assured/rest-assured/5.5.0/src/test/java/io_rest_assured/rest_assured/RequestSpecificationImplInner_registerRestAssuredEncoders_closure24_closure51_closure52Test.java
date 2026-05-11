/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplRegisterRestAssuredEncodersClosure24Closure51Closure52Access;
import io.restassured.internal.RequestSpecificationImplRegisterRestAssuredEncodersClosure24Closure51Closure52DirectAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RequestSpecificationImplInner_registerRestAssuredEncoders_closure24_closure51_closure52Test {
    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        Class<?> resolvedClosureClass = RequestSpecificationImplRegisterRestAssuredEncodersClosure24Closure51Closure52DirectAccess
                .resolveWithCompilerGeneratedClassResolver(activeClosureClassName());
        Class<?> resolvedOwnerClass = RequestSpecificationImplRegisterRestAssuredEncodersClosure24Closure51Closure52DirectAccess
                .resolveWithCompilerGeneratedClassResolver(resolvableRequestSpecificationImplClassName());

        assertEquals(activeClosureClassName(), resolvedClosureClass.getName());
        assertEquals(RequestSpecificationImpl.class, resolvedOwnerClass);
    }

    @Test
    void compilerGeneratedClassResolverConvertsMissingClass() {
        NoClassDefFoundError error = assertThrows(NoClassDefFoundError.class,
                () -> RequestSpecificationImplRegisterRestAssuredEncodersClosure24Closure51Closure52DirectAccess
                        .resolveWithCompilerGeneratedClassResolver(missingClassName()));

        assertEquals(missingClassName(), error.getMessage());
    }

    @Test
    void methodHandleClassResolverUsesClassForName() throws Throwable {
        Class<?> resolvedClosureClass = RequestSpecificationImplRegisterRestAssuredEncodersClosure24Closure51Closure52Access
                .resolveWithCompilerGeneratedClassResolver(activeClosureClassName());
        Class<?> resolvedOwnerClass = RequestSpecificationImplRegisterRestAssuredEncodersClosure24Closure51Closure52Access
                .resolveWithCompilerGeneratedClassResolver(resolvableRequestSpecificationImplClassName());

        assertEquals(activeClosureClassName(), resolvedClosureClass.getName());
        assertEquals(RequestSpecificationImpl.class, resolvedOwnerClass);
    }

    private static String activeClosureClassName() {
        return String.join("",
                "io.restassured.internal.RequestSpecificationImpl",
                "$_registerRestAssuredEncoders_closure24_closure51_closure52");
    }

    private static String missingClassName() {
        return String.join(".",
                RequestSpecificationImplInner_registerRestAssuredEncoders_closure24_closure51_closure52Test.class
                        .getPackageName(),
                "MissingRestAssuredClosure52Dependency");
    }

    private static String resolvableRequestSpecificationImplClassName() {
        return System.getProperty(
                "io.restassured.request-specification-impl-class",
                RequestSpecificationImpl.class.getName());
    }
}
