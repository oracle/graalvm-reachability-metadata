/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.AlgorithmParameters;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.spec.GCMParameterSpec;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.internal.asn1.cms.GCMParameters;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class GcmSpecUtilAnonymous2Test {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void gcmAlgorithmParametersEncodeJdkGcmParameterSpec() throws Exception {
        byte[] nonce = new byte[] {
            0x40, 0x41, 0x42, 0x43,
            0x44, 0x45, 0x46, 0x47,
            0x48, 0x49, 0x4a, 0x4b
        };
        GCMParameterSpec inputSpec = new GCMParameterSpec(96, nonce);
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("GCM", bouncyCastleFipsProvider());

        try {
            parameters.init(inputSpec);
            GCMParameters gcmParameters = GCMParameters.getInstance(ASN1Primitive.fromByteArray(parameters.getEncoded()));
            assertArrayEquals(nonce, gcmParameters.getNonce());
            assertEquals(12, gcmParameters.getIcvLen());
        } catch (InvalidParameterSpecException e) {
            assertTrue(e.getMessage().startsWith("AlgorithmParameterSpec class not recognized:"));
        }
    }

    @Test
    void extractGcmParametersInvokesJdkGcmSpecAccessors() throws Throwable {
        byte[] nonce = new byte[] {
            0x50, 0x51, 0x52, 0x53,
            0x54, 0x55, 0x56, 0x57,
            0x58, 0x59, 0x5a, 0x5b
        };
        MethodHandle extractGcmParameters = gcmSpecUtilLookup().findStatic(
            gcmSpecUtilType(),
            "extractGcmParameters",
            MethodType.methodType(GCMParameters.class, AlgorithmParameterSpec.class));

        try {
            GCMParameters gcmParameters = (GCMParameters)extractGcmParameters.invoke(new GCMParameterSpec(104, nonce));
            assertArrayEquals(nonce, gcmParameters.getNonce());
            assertEquals(13, gcmParameters.getIcvLen());
        } catch (InvalidParameterSpecException e) {
            assertTrue(e.getMessage().startsWith("cannot process GCMParameterSpec:"));
        }
    }

    private static MethodHandles.Lookup gcmSpecUtilLookup()
        throws ClassNotFoundException, IllegalAccessException {
        return MethodHandles.privateLookupIn(gcmSpecUtilType(), MethodHandles.lookup());
    }

    private static Class<?> gcmSpecUtilType() throws ClassNotFoundException {
        return Class.forName("org.bouncycastle.jcajce.provider.GcmSpecUtil");
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return TestProviders.bcFipsProvider();
    }
}
