/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.PreemptiveAuthSpecImpl;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PreemptiveAuthSpecImplTest {
    private static final String DYNAMIC_RESOLUTION_TARGET =
            "io.restassured.mapper.ObjectMapperDeserializationContext";

    /**
     * Minimal same-package helper whose class initializer directly invokes
     * `PreemptiveAuthSpecImpl.class$(String)`.
     */
    private static final byte[] DIRECT_INVOKER_CLASS = Base64.getMimeDecoder().decode("""
            yv66vgAAADQAFgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW
            CAAIAQA4aW8ucmVzdGFzc3VyZWQubWFwcGVyLk9iamVjdE1hcHBlckRlc2VyaWFsaXphdGlvbkNv
            bnRleHQKAAoACwcADAwADQAOAQAuaW8vcmVzdGFzc3VyZWQvaW50ZXJuYWwvUHJlZW1wdGl2ZUF1
            dGhTcGVjSW1wbAEABmNsYXNzJAEAJShMamF2YS9sYW5nL1N0cmluZzspTGphdmEvbGFuZy9DbGFz
            czsHABABADtpby9yZXN0YXNzdXJlZC9pbnRlcm5hbC9QcmVlbXB0aXZlQXV0aFNwZWNJbXBsRGly
            ZWN0SW52b2tlcgEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAAg8Y2xpbml0PgEAClNvdXJjZUZp
            bGUBAChQcmVlbXB0aXZlQXV0aFNwZWNJbXBsRGlyZWN0SW52b2tlci5qYXZhACEADwACAAAAAAAC
            AAEABQAGAAEAEQAAAB0AAQABAAAABSq3AAGxAAAAAQASAAAABgABAAAAAwAIABMABgABABEAAAAj
            AAEAAAAAAAcSB7gACVexAAAAAQASAAAACgACAAAABQAGAAYAAQAUAAAAAgAV
            """);

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    PreemptiveAuthSpecImpl.class,
                    "class$",
                    new Object[] {DYNAMIC_RESOLUTION_TARGET});

            assertEquals(DYNAMIC_RESOLUTION_TARGET, ((Class<?>) resolvedClass).getName());
        } catch (NoClassDefFoundError error) {
            assertGroovyInitializationFailure(error);
        }
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughMethodHandle() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                PreemptiveAuthSpecImpl.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                PreemptiveAuthSpecImpl.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(DYNAMIC_RESOLUTION_TARGET);

        assertEquals(DYNAMIC_RESOLUTION_TARGET, resolvedClass.getName());
    }

    @Test
    void directInvokerReachesCompilerGeneratedClassResolver() throws IllegalAccessException {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    PreemptiveAuthSpecImpl.class,
                    MethodHandles.lookup());
            Class<?> directInvokerClass = lookup.defineClass(DIRECT_INVOKER_CLASS);

            lookup.ensureInitialized(directInvokerClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void assertGroovyInitializationFailure(NoClassDefFoundError error) {
        String message = error.getMessage();
        assertTrue(
                "Could not initialize class groovy.lang.GroovySystem".equals(message)
                        || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message));
    }
}
