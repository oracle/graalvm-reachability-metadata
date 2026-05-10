/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.RestAssured;
import io.restassured.authentication.AuthenticationScheme;
import io.restassured.authentication.OAuth2Scheme;
import io.restassured.authentication.OAuthSignature;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OAuth2SchemeTest {
    private static final String ACCESS_TOKEN = "access-token";
    private static final String DYNAMIC_RESOLUTION_TARGET =
            "io.restassured.mapper.ObjectMapperDeserializationContext";

    /**
     * Minimal same-package helper whose class initializer directly invokes
     * `OAuth2Scheme.class$(String)`.
     */
    private static final byte[] DIRECT_INVOKER_CLASS = Base64.getMimeDecoder().decode("""
            yv66vgAAADcAEwEAN2lvL3Jlc3Rhc3N1cmVkL2F1dGhlbnRpY2F0aW9uL09BdXRoMlNjaGVtZURp
            cmVjdEludm9rZXIHAAEBABBqYXZhL2xhbmcvT2JqZWN0BwADAQAGPGluaXQ+AQADKClWDAAFAAYK
            AAQABwEABENvZGUBAAg8Y2xpbml0PgEAKmlvL3Jlc3Rhc3N1cmVkL2F1dGhlbnRpY2F0aW9uL09B
            dXRoMlNjaGVtZQcACwEABmNsYXNzJAEAJShMamF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9D
            bGFzczsMAA0ADgoADAAPAQA4aW8ucmVzdGFzc3VyZWQubWFwcGVyLk9iamVjdE1hcHBlckRlc2Vy
            aWFsaXphdGlvbkNvbnRleHQIABEAIQACAAQAAAAAAAIAAQAFAAYAAQAJAAAAEQABAAEAAAAFKrcA
            CLEAAAAAAAgACgAGAAEACQAAABMAAQAAAAAABxISuAAQV7EAAAAAAAA=
            """);

    @Test
    @Order(1)
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                OAuth2Scheme.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                OAuth2Scheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(DYNAMIC_RESOLUTION_TARGET);

        assertEquals(DYNAMIC_RESOLUTION_TARGET, resolvedClass.getName());
    }

    @Test
    @Order(2)
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    OAuth2Scheme.class,
                    "class$",
                    new Object[] {DYNAMIC_RESOLUTION_TARGET});

            assertEquals(DYNAMIC_RESOLUTION_TARGET, ((Class<?>) resolvedClass).getName());
        } catch (NoClassDefFoundError error) {
            assertGroovyInitializationFailure(error);
        }
    }

    @Test
    @Order(3)
    void directInvokerReachesCompilerGeneratedClassResolver() throws IllegalAccessException {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    OAuth2Scheme.class,
                    MethodHandles.lookup());
            Class<?> directInvokerClass = lookup.defineClass(DIRECT_INVOKER_CLASS);

            lookup.ensureInitialized(directInvokerClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    @Order(4)
    void factoryCreatesConfiguredQueryStringOAuth2Scheme() {
        AuthenticationScheme authenticationScheme = RestAssured.oauth2(ACCESS_TOKEN, OAuthSignature.QUERY_STRING);

        OAuth2Scheme oauth2Scheme = assertInstanceOf(OAuth2Scheme.class, authenticationScheme);
        assertEquals(ACCESS_TOKEN, oauth2Scheme.getAccessToken());
        assertSame(OAuthSignature.QUERY_STRING, oauth2Scheme.getSignature());
    }

    private static void assertGroovyInitializationFailure(NoClassDefFoundError error) {
        String message = error.getMessage();
        assertTrue(
                "Could not initialize class groovy.lang.GroovySystem".equals(message)
                        || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message));
    }
}
