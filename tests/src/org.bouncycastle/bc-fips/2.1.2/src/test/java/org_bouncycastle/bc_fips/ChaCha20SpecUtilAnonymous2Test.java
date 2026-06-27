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

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.junit.jupiter.api.Test;

public class ChaCha20SpecUtilAnonymous2Test {
    private static final String CHACHA20_SPEC_UTIL_CLASS_NAME =
        "org.bouncycastle.jcajce.provider.ChaCha20SpecUtil";

    @Test
    void extractChaCha20ParametersInvokesSpecAccessors() throws Throwable {
        byte[] nonce = new byte[] {
            0x20, 0x21, 0x22, 0x23,
            0x24, 0x25, 0x26, 0x27,
            0x28, 0x29, 0x2a, 0x2b
        };
        int counter = 7;
        AlgorithmParameterSpec parameterSpec = new ChaCha20ParameterSpec(nonce, counter);
        MethodHandle extractChaCha20Parameters = chaCha20SpecUtilLookup().findStatic(
            chaCha20SpecUtilType(),
            "extractChaCha20Parameters",
            MethodType.methodType(ASN1Sequence.class, AlgorithmParameterSpec.class));

        try {
            Object result = extractChaCha20Parameters.invoke(parameterSpec);
            ASN1Sequence parameters = assertInstanceOf(ASN1Sequence.class, result);
            assertEquals(2, parameters.size());
            assertArrayEquals(nonce, ASN1OctetString.getInstance(parameters.getObjectAt(0)).getOctets());
            assertEquals(counter, ASN1Integer.getInstance(parameters.getObjectAt(1)).getValue().intValue());
        } catch (InvalidParameterSpecException e) {
            assertTrue(e.getMessage().startsWith("cannot process ChaCha20ParameterSpec:"));
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
