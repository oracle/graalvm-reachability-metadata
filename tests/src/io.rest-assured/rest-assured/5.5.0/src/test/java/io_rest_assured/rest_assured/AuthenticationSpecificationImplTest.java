/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.RestAssured;
import io.restassured.authentication.AuthenticationScheme;
import io.restassured.authentication.CertAuthScheme;
import io.restassured.authentication.CertificateAuthSettings;
import io.restassured.internal.AuthenticationSpecificationImpl;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static io.restassured.authentication.CertificateAuthSettings.certAuthSettings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AuthenticationSpecificationImplTest {
    private static final String CERTIFICATE_PATH = "client-keystore.jks";
    private static final String PASSWORD = "changeit";
    private static final int TLS_PORT = 8443;

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = (Class<?>) classResolver().invokeWithArguments(
                "io.restassured.internal.AuthenticationSpecificationImpl");

        assertSame(AuthenticationSpecificationImpl.class, resolvedClass);
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    AuthenticationSpecificationImpl.class,
                    "class$",
                    new Object[] {"io.restassured.internal.AuthenticationSpecificationImpl"});

            assertSame(AuthenticationSpecificationImpl.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            assertEquals("Could not initialize class groovy.lang.GroovySystem", error.getMessage());
        }
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> classResolver().invokeWithArguments(
                        "io.restassured.internal.AuthenticationSpecificationImplMissingClass"));

        assertEquals("io.restassured.internal.AuthenticationSpecificationImplMissingClass", error.getMessage());
    }

    @Test
    void certificateAuthenticationConfiguresRequestSpecificationWithCustomSettings() {
        RequestSpecification requestSpecification = RestAssured.given();
        CertificateAuthSettings settings = certAuthSettings().port(TLS_PORT).allowAllHostnames();

        RequestSpecification configuredSpecification = requestSpecification.auth()
                .certificate(CERTIFICATE_PATH, PASSWORD, settings);

        assertSame(requestSpecification, configuredSpecification);
        AuthenticationScheme authenticationScheme = SpecificationQuerier.query(configuredSpecification)
                .getAuthenticationScheme();
        CertAuthScheme certAuthScheme = assertInstanceOf(CertAuthScheme.class, authenticationScheme);
        assertEquals(CERTIFICATE_PATH, certAuthScheme.getPathToTrustStore());
        assertEquals(PASSWORD, certAuthScheme.getTrustStorePassword());
        assertEquals(settings.getPort(), certAuthScheme.getPort());
        assertEquals(settings.getTrustStoreType(), certAuthScheme.getTrustStoreType());
        assertSame(settings.getX509HostnameVerifier(), certAuthScheme.getX509HostnameVerifier());
    }

    private static MethodHandle classResolver() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                AuthenticationSpecificationImpl.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                AuthenticationSpecificationImpl.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }
}
