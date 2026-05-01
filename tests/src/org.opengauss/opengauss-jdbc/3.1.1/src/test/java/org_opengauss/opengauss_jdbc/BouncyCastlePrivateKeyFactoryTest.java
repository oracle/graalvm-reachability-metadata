/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ssl.BouncyCastlePrivateKeyFactory;

import java.security.Provider;

import static org.assertj.core.api.Assertions.assertThat;

public class BouncyCastlePrivateKeyFactoryTest {
    @Test
    void createsBouncyCastleProvider() throws Exception {
        Provider provider = BouncyCastlePrivateKeyFactory.initBouncyCastleProvider();

        assertThat(provider).isNotNull();
        assertThat(provider.getName()).isEqualTo("BC");
    }
}
