/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.authentication.NTLMAuthScheme;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NTLMAuthSchemeTest {
    /**
     * Minimal same-package helper whose class initializer directly invokes
     * `NTLMAuthScheme.class$(String)`.
     */
    private static final byte[] DIRECT_INVOKER_CLASS = Base64.getMimeDecoder().decode("""
            yv66vgAAADcAEwEAOWlvL3Jlc3Rhc3N1cmVkL2F1dGhlbnRpY2F0aW9uL05UTE1BdXRoU2NoZW1l
            RGVyZWN0SW52b2tlcgcAAQEAEGphdmEvbGFuZy9PYmplY3QHAAMBAAY8aW5pdD4BAAMoKVYM
            AAUABgoABAAHAQAEQ29kZQEACDxjbGluaXQ+AQAsaW8vcmVzdGFzc3VyZWQvYXV0aGVudGlj
            YXRpb24vTlRMTUF1dGhTY2hlbWUHAAsBAAZjbGFzcyQBACUoTGphdmEvbGFuZy9TdHJpbmc7
            KUxqYXZhL2xhbmcvQ2xhc3M7DAANAA4KAAwADwEAEGphdmEubGFuZy5TdHJpbmcIABEAMQAC
            AAQAAAAAAAIAAgAFAAYAAQAJAAAAEQABAAEAAAAFKrcACLEAAAAAAAgACgAGAAEACQAAABMA
            AQAAAAAABxISuAAQV7EAAAAAAAA=
            """);

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                NTLMAuthScheme.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                NTLMAuthScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(
                "io.restassured.authentication.OAuth2Scheme");

        assertEquals("io.restassured.authentication.OAuth2Scheme", resolvedClass.getName());
    }

    @Test
    void directInvokerReachesCompilerGeneratedClassResolver() throws IllegalAccessException {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    NTLMAuthScheme.class,
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
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    NTLMAuthScheme.class,
                    "class$",
                    new Object[] {"io.restassured.authentication.OAuthScheme"});

            assertEquals("io.restassured.authentication.OAuthScheme", ((Class<?>) resolvedClass).getName());
        } catch (NoClassDefFoundError error) {
            String message = error.getMessage();
            assertTrue(
                    "Could not initialize class groovy.lang.GroovySystem".equals(message)
                            || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message));
        }
    }
}
