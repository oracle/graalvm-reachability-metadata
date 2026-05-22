/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15on;

import java.security.Provider;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BouncyCastleProviderTest {
    @Test
    void providerInitializesCoreProviderEntries() {
        Provider previousProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (previousProvider != null) {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        }

        try {
            BouncyCastleProvider provider = new BouncyCastleProvider();
            int position = Security.addProvider(provider);

            assertThat(position).isPositive();
            assertThat(provider.getName()).isEqualTo(BouncyCastleProvider.PROVIDER_NAME);
            assertThat(provider.getInfo()).contains("Security Provider");
            assertThat(provider.getVersion()).isEqualTo(1.48);
            assertThat(provider.get("X509Store.CERTIFICATE/COLLECTION"))
                .isEqualTo("org.bouncycastle.jce.provider.X509StoreCertCollection");
            assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isSameAs(provider);
        } finally {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (previousProvider != null) {
                Security.addProvider(previousProvider);
            }
        }
    }
}
