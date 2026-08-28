/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.security.Security;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.util.DumpInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DumpInfoTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void reportsTheInitializedFipsModuleStatus() {
        String information = DumpInfo.getInfoString(false);

        assertThat(information).contains("Version Info: BouncyCastle Security Provider");
        assertThat(information).contains("FIPS Ready Status: READY");
        assertThat(information).contains("Module SHA-256 HMAC:");
    }
}
