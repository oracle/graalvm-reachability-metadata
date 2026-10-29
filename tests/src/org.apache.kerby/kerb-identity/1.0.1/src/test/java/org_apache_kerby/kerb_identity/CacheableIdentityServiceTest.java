/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_identity;

import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.identity.CacheableIdentityService;
import org.apache.kerby.kerberos.kerb.identity.KrbIdentity;
import org.apache.kerby.kerberos.kerb.identity.backend.BackendConfig;
import org.apache.kerby.kerberos.kerb.identity.backend.MemoryIdentityBackend;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;
import org.apache.kerby.kerberos.kerb.type.kdc.KdcClientRequest;
import org.apache.kerby.kerberos.kerb.type.ticket.EncTicketPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CacheableIdentityServiceTest {
    private final MemoryIdentityBackend backend = new MemoryIdentityBackend();
    private CacheableIdentityService service;

    @BeforeEach
    void startBackend() throws KrbException {
        BackendConfig config = new BackendConfig();
        backend.setConfig(config);
        backend.initialize();
        backend.start();
        service = new CacheableIdentityService(config, backend);
    }

    @AfterEach
    void stopBackend() throws KrbException {
        try {
            backend.stop();
        } finally {
            backend.release();
        }
    }

    @Test
    void loadsExistingAccountsAndKeepsUpdatesAvailable() throws KrbException {
        KrbIdentity alice = new KrbIdentity("alice@EXAMPLE.COM");
        backend.addIdentity(alice);

        assertThat(service.getIdentity(alice.getPrincipalName())).isSameAs(alice);
        assertThat(service.getIdentity(alice.getPrincipalName())).isSameAs(alice);
        assertThat(service.getIdentities()).containsExactly(alice.getPrincipalName());

        alice.setLocked(true);
        alice.setKeyVersion(5);
        service.updateIdentity(alice);

        KrbIdentity cached = service.getIdentity(alice.getPrincipalName());
        assertThat(cached.isLocked()).isTrue();
        assertThat(cached.getKeyVersion()).isEqualTo(5);
        assertThat(backend.getIdentity(alice.getPrincipalName()).isLocked()).isTrue();
        assertThat(backend.getIdentity(alice.getPrincipalName()).getKeyVersion()).isEqualTo(5);
    }

    @Test
    void addsDeletesAndReloadsAccountsThroughTheService() throws KrbException {
        String principal = "service/host@EXAMPLE.COM";
        assertThat(service.getIdentity(principal)).isNull();

        KrbIdentity identity = new KrbIdentity(principal);
        assertThat(service.addIdentity(identity)).isSameAs(identity);
        assertThat(service.getIdentity(principal)).isSameAs(identity);
        assertThat(backend.getIdentity(principal)).isSameAs(identity);
        assertThat(service.getIdentities()).containsExactly(principal);

        service.deleteIdentity(principal);
        assertThat(service.getIdentity(principal)).isNull();
        assertThat(backend.getIdentity(principal)).isNull();
        assertThat(service.getIdentities()).isEmpty();

        KrbIdentity replacement = new KrbIdentity(principal);
        replacement.setKeyVersion(6);
        backend.addIdentity(replacement);
        assertThat(service.getIdentity(principal)).isSameAs(replacement);
        assertThat(service.getIdentity(principal).getKeyVersion()).isEqualTo(6);
    }

    @Test
    void delegatesAuthorizationAndReportsTransactionSupport() throws KrbException {
        KdcClientRequest request = new KdcClientRequest();
        request.setClientPrincipal(new PrincipalName("alice@EXAMPLE.COM"));
        request.setToken(false);

        assertThat(service.getIdentityAuthorizationData(request, new EncTicketPart())).isNull();
        assertThat(service.supportBatchTrans()).isFalse();
        assertThatThrownBy(service::startBatchTrans).isInstanceOf(KrbException.class);
    }
}
