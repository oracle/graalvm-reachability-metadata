/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.RestAssured;
import io.restassured.authentication.AuthenticationScheme;
import io.restassured.authentication.OAuthScheme;
import io.restassured.authentication.OAuthSignature;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class OAuthSchemeTest {
    private static final String CONSUMER_KEY = "consumer-key";
    private static final String CONSUMER_SECRET = "consumer-secret";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String SECRET_TOKEN = "secret-token";
    private static final String DYNAMIC_RESOLUTION_TARGET =
            "io.restassured.mapper.ObjectMapperDeserializationContext";

    /**
     * Minimal same-package helper whose class initializer directly invokes
     * `OAuthScheme.class$(String)`.
     */
    private static final byte[] DIRECT_INVOKER_CLASS = Base64.getMimeDecoder().decode("""
            yv66vgAAADcAEwEANmlvL3Jlc3Rhc3N1cmVkL2F1dGhlbnRpY2F0aW9uL09BdXRoU2NoZW1lRGly
            ZWN0SW52b2tlcgcAAQEAEGphdmEvbGFuZy9PYmplY3QHAAMBAAY8aW5pdD4BAAMoKVYMAAUABgoA
            BAAHAQAEQ29kZQEACDxjbGluaXQ+AQApaW8vcmVzdGFzc3VyZWQvYXV0aGVudGljYXRpb24vT0F1
            dGhTY2hlbWUHAAsBAAZjbGFzcyQBACUoTGphdmEvbGFuZy9TdHJpbmc7KUxqYXZhL2xhbmcvQ2xh
            c3M7DAANAA4KAAwADwEAOGlvLnJlc3Rhc3N1cmVkLm1hcHBlci5PYmplY3RNYXBwZXJEZXNlcmlh
            bGl6YXRpb25Db250ZXh0CAARACEAAgAEAAAAAAACAAEABQAGAAEACQAAABEAAQABAAAABSq3AAix
            AAAAAAAIAAoABgABAAkAAAATAAEAAAAAAAcSErgAEFexAAAAAAAA
            """);

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                OAuthScheme.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                OAuthScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(DYNAMIC_RESOLUTION_TARGET);

        assertEquals(DYNAMIC_RESOLUTION_TARGET, resolvedClass.getName());
    }

    @Test
    void directInvokerReachesCompilerGeneratedClassResolver() throws IllegalAccessException {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    OAuthScheme.class,
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
    void factoryCreatesConfiguredOAuthScheme() {
        AuthenticationScheme authenticationScheme = RestAssured.oauth(
                CONSUMER_KEY,
                CONSUMER_SECRET,
                ACCESS_TOKEN,
                SECRET_TOKEN,
                OAuthSignature.QUERY_STRING);

        OAuthScheme oauthScheme = assertInstanceOf(OAuthScheme.class, authenticationScheme);
        assertEquals(CONSUMER_KEY, oauthScheme.getConsumerKey());
        assertEquals(CONSUMER_SECRET, oauthScheme.getConsumerSecret());
        assertEquals(ACCESS_TOKEN, oauthScheme.getAccessToken());
        assertEquals(SECRET_TOKEN, oauthScheme.getSecretToken());
        assertSame(OAuthSignature.QUERY_STRING, oauthScheme.getSignature());
    }
}
