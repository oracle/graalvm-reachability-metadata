/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LayeredBuildArgsUtilsTests {
    @Test
    void sharedBasesKeepJdkProvidersAndDedicatedBasesKeepAllProviders() {
        String provider = "org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider";
        String prefix = "-H:AdditionalSecurityProviders=";
        List<String> policy = List.of("--verbose", prefix + provider,
                prefix + "sun.security.provider.Sun," + provider + ",sun.security.pkcs11.SunPKCS11",
                prefix + "sun.security.provider.Sun", "-H:+AddAllCharsets");

        assertThat(LayeredBuildArgsUtils.sharedLayerArguments(policy)).containsExactly(
                "--verbose", prefix + "sun.security.provider.Sun,sun.security.pkcs11.SunPKCS11",
                prefix + "sun.security.provider.Sun", "-H:+AddAllCharsets");
        assertThat(LayeredBuildArgsUtils.dedicatedLayerArguments(policy, List.of(), List.of()))
                .containsExactlyElementsOf(policy);
    }

}
