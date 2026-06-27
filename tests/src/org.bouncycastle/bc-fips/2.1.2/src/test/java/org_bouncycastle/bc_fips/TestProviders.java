/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.security.Provider;
import java.security.Security;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;

final class TestProviders {
    private TestProviders() {
    }

    static Provider bcFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }

        Provider newProvider = new BouncyCastleFipsProvider();
        Security.addProvider(newProvider);

        Provider registeredProvider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        return registeredProvider != null ? registeredProvider : newProvider;
    }
}
