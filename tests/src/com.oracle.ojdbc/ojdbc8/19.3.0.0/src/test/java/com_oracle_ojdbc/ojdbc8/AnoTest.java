/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import oracle.net.ano.Ano;
import oracle.net.ns.ClientProfile;
import oracle.net.ns.SessionAtts;
import org.junit.jupiter.api.Test;

public class AnoTest {
    @Test
    void initializesSecurityServicesAndRetainsSessionKeyMaterial() throws Exception {
        Properties options = new Properties();
        options.setProperty("oracle.net.encryption_client", "ACCEPTED");
        options.setProperty("oracle.net.crypto_checksum_client", "ACCEPTED");
        SessionAtts session = new SessionAtts(null, 8192, 8192, false, false);
        session.profile = new ClientProfile(options);
        Ano security = new Ano();

        security.init(session, false, false);
        security.setO3logSessionKey(new byte[] {11, 22, 33, 44});
        security.setRenewKey(true);

        assertThat(session.ano).isSameAs(security);
        assertThat(security.getNAFlags()).isPositive();
        assertThat(security.getO3logSessionKey()).containsExactly(11, 22, 33, 44);
        assertThat(security.getRenewKey()).isTrue();
        security.setRenewKey(false);
        assertThat(security.getRenewKey()).isFalse();
    }
}
