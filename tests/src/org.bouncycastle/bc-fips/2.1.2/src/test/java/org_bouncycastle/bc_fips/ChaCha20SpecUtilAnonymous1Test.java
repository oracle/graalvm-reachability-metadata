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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.spec.ChaCha20ParameterSpec;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.junit.jupiter.api.Test;

public class ChaCha20SpecUtilAnonymous1Test {
    private static final String CHACHA20_SPEC_UTIL_CLASS_NAME =
        "org.bouncycastle.jcajce.provider.ChaCha20SpecUtil";

    @Test
    void extractChaCha20SpecRunsPrivilegedConstructorAction() throws Throwable {
        byte[] nonce = new byte[] {
            0x10, 0x11, 0x12, 0x13,
            0x14, 0x15, 0x16, 0x17,
            0x18, 0x19, 0x1a, 0x1b
        };
        MethodHandle extractChaCha20Spec = chaCha20SpecUtilLookup().findStatic(
            chaCha20SpecUtilType(),
            "extractChaCha20Spec",
            MethodType.methodType(AlgorithmParameterSpec.class, ASN1Primitive.class));

        try {
            Object result = extractChaCha20Spec.invoke(new DEROctetString(nonce));
            ChaCha20ParameterSpec chaCha20Spec = assertInstanceOf(ChaCha20ParameterSpec.class, result);
            assertArrayEquals(nonce, chaCha20Spec.getNonce());
            assertEquals(0, chaCha20Spec.getCounter());
        } catch (InvalidParameterSpecException e) {
            assertTrue(e.getMessage().startsWith("construction failed:"));
        }
    }

    private static MethodHandles.Lookup chaCha20SpecUtilLookup()
        throws ClassNotFoundException, IllegalAccessException {
        return MethodHandles.privateLookupIn(chaCha20SpecUtilType(), MethodHandles.lookup());
    }

    private static Class<?> chaCha20SpecUtilType() throws ClassNotFoundException {
        return Class.forName(CHACHA20_SPEC_UTIL_CLASS_NAME);
    }
}
