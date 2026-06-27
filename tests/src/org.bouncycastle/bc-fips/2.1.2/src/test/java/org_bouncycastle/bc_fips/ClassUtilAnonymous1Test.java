/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Provider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ClassUtilAnonymous1Test {
    private static final String CLASS_UTIL_CLASS_NAME = "org.bouncycastle.jcajce.provider.ClassUtil";
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";

    private static final byte[] AES_KEY = new byte[] {
        0x00, 0x01, 0x02, 0x03,
        0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0a, 0x0b,
        0x0c, 0x0d, 0x0e, 0x0f
    };
    private static final byte[] GCM_NONCE = new byte[] {
        0x10, 0x11, 0x12, 0x13,
        0x14, 0x15, 0x16, 0x17,
        0x18, 0x19, 0x1a, 0x1b
    };

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void extractMethodRunsPrivilegedGetDeclaredMethodAction() throws Throwable {
        MethodHandle extractMethod = classUtilLookup().findStatic(
            classUtilType(),
            "extractMethod",
            MethodType.methodType(Method.class, Class.class, String.class));

        Object result = extractMethod.invoke(BouncyCastleFipsProvider.class, "getDefaultSecureRandom");

        Method method = assertInstanceOf(Method.class, result);
        assertEquals("getDefaultSecureRandom", method.getName());
        assertEquals(0, method.getParameterCount());
    }

    private static MethodHandles.Lookup classUtilLookup()
        throws ClassNotFoundException, IllegalAccessException {
        return MethodHandles.privateLookupIn(classUtilType(), MethodHandles.lookup());
    }

    private static Class<?> classUtilType() throws ClassNotFoundException {
        return Class.forName(CLASS_UTIL_CLASS_NAME);
    }
}
