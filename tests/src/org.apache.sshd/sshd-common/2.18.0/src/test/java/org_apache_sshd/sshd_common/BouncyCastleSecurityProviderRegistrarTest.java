/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.sshd.common.util.security.bouncycastle.BouncyCastleSecurityProviderRegistrar;
import org.junit.jupiter.api.Test;

public class BouncyCastleSecurityProviderRegistrarTest {
    @Test
    void discoversBouncyCastleProviderThroughSupportProbe() {
        BouncyCastleSecurityProviderRegistrar registrar =
                new BouncyCastleSecurityProviderRegistrar();

        assertThat(registrar.isSupported()).isTrue();
        assertThat(registrar.getProviderName()).isEqualTo("BC");
        assertThat(registrar.getEdDSASupport()).isPresent();
    }
}
