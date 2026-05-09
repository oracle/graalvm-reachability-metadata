/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_server;

import org.apache.kerby.config.Config;
import org.apache.kerby.kerberos.kerb.identity.backend.BackendConfig;
import org.apache.kerby.kerberos.kerb.identity.backend.IdentityBackend;
import org.apache.kerby.kerberos.kerb.identity.backend.MemoryIdentityBackend;
import org.apache.kerby.kerberos.kerb.server.KdcUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KdcUtilTest {
    @Test
    void createsDefaultMemoryBackendFromConfiguration() throws Exception {
        BackendConfig backendConfig = new BackendConfig();

        IdentityBackend backend = KdcUtil.getBackend(backendConfig);

        assertThat(backend).isInstanceOf(MemoryIdentityBackend.class);
        Config configuredBackend = backend.getConfig();
        assertThat(configuredBackend).isSameAs(backendConfig);
    }
}
