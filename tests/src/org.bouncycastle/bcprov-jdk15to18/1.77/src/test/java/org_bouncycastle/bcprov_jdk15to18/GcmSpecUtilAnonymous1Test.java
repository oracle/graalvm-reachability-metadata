/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.AlgorithmParameters;
import java.security.CodeSource;
import java.security.Provider;

import javax.crypto.spec.GCMParameterSpec;

import org.bouncycastle.jcajce.provider.symmetric.util.GcmSpecUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class GcmSpecUtilAnonymous1Test {
    private static final byte[] NONCE = new byte[] {
        0x10, 0x11, 0x12, 0x13,
        0x14, 0x15, 0x16, 0x17,
        0x18, 0x19, 0x1a, 0x1b
    };

    @Test
    void gcmParametersAcceptAndReturnJdkGcmParameterSpec() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("GCM", provider());

        parameters.init(new GCMParameterSpec(128, NONCE));
        GCMParameterSpec extracted = parameters.getParameterSpec(GCMParameterSpec.class);

        assertEquals(128, extracted.getTLen());
        assertArrayEquals(NONCE, extracted.getIV());
    }

    @Test
    void isolatedLoaderInitializesGcmSpecUtil() throws Exception {
        try {
            CodeSource codeSource = GcmSpecUtil.class.getProtectionDomain().getCodeSource();
            URL bcprovLocation = codeSource.getLocation();

            try (URLClassLoader isolatedLoader = new URLClassLoader(
                new URL[] {bcprovLocation}, null)) {
                Class.forName(GcmSpecUtil.class.getName(), true, isolatedLoader);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Provider provider() {
        return new BouncyCastleProvider();
    }
}
