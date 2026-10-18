/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Provider;
import java.security.Security;
import org.apache.pulsar.common.util.SecurityUtility;
import org.junit.jupiter.api.Test;

public class SecurityUtilityTest {

    @Test
    void loadsAndRegistersBouncyCastleFromTheClientClasspath() throws Exception {
        final Provider provider = SecurityUtility.getBCProviderFromClassPath();

        assertThat(provider.getName()).isNotBlank();
        assertThat(Security.getProvider(provider.getName()).getName()).isEqualTo(provider.getName());
    }
}
