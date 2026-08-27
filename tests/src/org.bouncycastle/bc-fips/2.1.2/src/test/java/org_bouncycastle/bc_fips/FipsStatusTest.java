/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.security.Security;
import java.util.Arrays;

import org.bouncycastle.crypto.fips.FipsStatus;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FipsStatusTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void reportsAReadyProviderAndItsModuleIntegrityValue() {
        boolean ready = FipsStatus.isReady();
        String statusMessage = FipsStatus.getStatusMessage();
        byte[] moduleHmac = FipsStatus.getModuleHMAC();

        assertThat(ready).isTrue();
        assertThat(statusMessage).isEqualTo(FipsStatus.READY);
        assertThat(moduleHmac).hasSize(32);
        assertThat(Arrays.equals(moduleHmac, new byte[moduleHmac.length])).isFalse();
    }
}
