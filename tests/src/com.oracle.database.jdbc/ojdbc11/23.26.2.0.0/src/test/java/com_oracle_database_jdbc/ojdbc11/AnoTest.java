/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import oracle.jdbc.diagnostics.CommonDiagnosable;
import oracle.net.ano.Ano;
import oracle.net.ns.ClientProfile;
import oracle.net.ns.SessionAtts;
import org.junit.jupiter.api.Test;

public class AnoTest {
    @Test
    void initializesConfiguredNetworkSecurityServices() throws Exception {
        SessionAtts session =
                new SessionAtts(null, 8192, 8192, false, false, CommonDiagnosable.getInstance());
        session.profile = new ClientProfile(new Properties());
        Ano ano = new Ano();

        ano.init(session, false);

        assertThat(session.ano).isSameAs(ano);
        assertThat(ano.getNAFlags()).isPositive();
    }
}
