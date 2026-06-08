/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import javax.crypto.spec.GCMParameterSpec;

import org.bouncycastle.internal.asn1.cms.GCMParameters;
import org.bouncycastle.jcajce.provider.symmetric.util.GcmSpecUtil;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class GcmSpecUtilAnonymous2Test {
    private static final byte[] NONCE = new byte[] {
        0x21, 0x22, 0x23, 0x24,
        0x25, 0x26, 0x27, 0x28,
        0x29, 0x2a, 0x2b, 0x2c
    };

    @Test
    void isolatedInitializationDiscoversJdkGcmParameterSpecMethods() throws Exception {
        try {
            CodeSource codeSource = GcmSpecUtil.class.getProtectionDomain().getCodeSource();
            URL bcprovLocation = codeSource.getLocation();

            try (URLClassLoader isolatedLoader = new URLClassLoader(
                new URL[] {bcprovLocation}, ClassLoader.getPlatformClassLoader())) {
                Class.forName(
                    "org.bouncycastle.jcajce.provider.symmetric.util.GcmSpecUtil",
                    true,
                    isolatedLoader);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void jdkGcmParameterSpecMethodsAreDiscoveredAndUsed() throws Exception {
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, NONCE);

        assertTrue(GcmSpecUtil.gcmSpecExists());
        assertTrue(GcmSpecUtil.gcmSpecExtractable());
        assertTrue(GcmSpecUtil.isGcmSpec(parameterSpec));
        assertTrue(GcmSpecUtil.isGcmSpec(GCMParameterSpec.class));

        GCMParameters parameters = GcmSpecUtil.extractGcmParameters(parameterSpec);

        assertArrayEquals(NONCE, parameters.getNonce());
        assertEquals(16, parameters.getIcvLen());
    }
}
