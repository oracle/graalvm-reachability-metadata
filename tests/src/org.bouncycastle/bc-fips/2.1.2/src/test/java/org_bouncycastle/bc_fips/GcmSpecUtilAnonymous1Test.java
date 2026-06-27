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
import java.security.AlgorithmParameters;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.spec.GCMParameterSpec;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.internal.asn1.cms.GCMParameters;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class GcmSpecUtilAnonymous1Test {
    private static final String GCM_SPEC_UTIL_CLASS_NAME = "org.bouncycastle.jcajce.provider.GcmSpecUtil";
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void extractGcmSpecRunsPrivilegedConstructorAction() throws Throwable {
        byte[] nonce = new byte[] {
            0x30, 0x31, 0x32, 0x33,
            0x34, 0x35, 0x36, 0x37,
            0x38, 0x39, 0x3a, 0x3b
        };
        MethodHandles.Lookup lookup = gcmSpecUtilLookup();
        MethodHandle gcmSpecExists = lookup.findStatic(
            gcmSpecUtilType(),
            "gcmSpecExists",
            MethodType.methodType(boolean.class));
        MethodHandle extractGcmSpec = lookup.findStatic(
            gcmSpecUtilType(),
            "extractGcmSpec",
            MethodType.methodType(AlgorithmParameterSpec.class, ASN1Primitive.class));

        assertTrue((boolean)gcmSpecExists.invoke());
        Object result = extractGcmSpec.invoke(new GCMParameters(nonce, 16).toASN1Primitive());
        GCMParameterSpec gcmSpec = assertInstanceOf(GCMParameterSpec.class, result);
        assertEquals(128, gcmSpec.getTLen());
        assertArrayEquals(nonce, gcmSpec.getIV());
    }

    @Test
    void gcmAlgorithmParametersDecodeJdkGcmParameterSpec() throws Exception {
        Provider provider = bouncyCastleFipsProvider();
        byte[] nonce = new byte[] {
            0x20, 0x21, 0x22, 0x23,
            0x24, 0x25, 0x26, 0x27,
            0x28, 0x29, 0x2a, 0x2b
        };
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("GCM", provider);

        parameters.init(new GCMParameters(nonce, 16).toASN1Primitive().getEncoded());
        AlgorithmParameterSpec outputSpec = parameters.getParameterSpec(AlgorithmParameterSpec.class);
        GCMParameterSpec gcmSpec = assertInstanceOf(GCMParameterSpec.class, outputSpec);

        assertEquals(128, gcmSpec.getTLen());
        assertArrayEquals(nonce, gcmSpec.getIV());

        GCMParameterSpec typedGcmSpec = parameters.getParameterSpec(GCMParameterSpec.class);
        assertEquals(128, typedGcmSpec.getTLen());
        assertArrayEquals(nonce, typedGcmSpec.getIV());
    }

    private static MethodHandles.Lookup gcmSpecUtilLookup()
        throws ClassNotFoundException, IllegalAccessException {
        return MethodHandles.privateLookupIn(gcmSpecUtilType(), MethodHandles.lookup());
    }

    private static Class<?> gcmSpecUtilType() throws ClassNotFoundException {
        return Class.forName(GCM_SPEC_UTIL_CLASS_NAME);
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return TestProviders.bcFipsProvider();
    }
}
