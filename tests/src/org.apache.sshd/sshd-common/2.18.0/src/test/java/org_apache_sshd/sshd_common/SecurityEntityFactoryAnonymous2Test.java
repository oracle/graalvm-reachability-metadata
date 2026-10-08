/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;

import org.apache.sshd.common.util.security.SecurityEntityFactory;
import org.junit.jupiter.api.Test;

public class SecurityEntityFactoryAnonymous2Test {
    @Test
    void obtainsAnEntityFromTheNamedProviderFactory() throws Exception {
        SecurityEntityFactory<MessageDigest> factory =
                SecurityEntityFactory.toNamedProviderFactory(MessageDigest.class, "SUN");

        assertThat(factory.getInstance("SHA-1").getDigestLength()).isEqualTo(20);
    }
}
