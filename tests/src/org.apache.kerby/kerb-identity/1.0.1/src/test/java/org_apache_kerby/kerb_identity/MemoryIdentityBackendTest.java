/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_identity;

import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.identity.KrbIdentity;
import org.apache.kerby.kerberos.kerb.identity.backend.BackendConfig;
import org.apache.kerby.kerberos.kerb.identity.backend.MemoryIdentityBackend;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;
import org.apache.kerby.kerberos.kerb.type.kdc.KdcClientRequest;
import org.apache.kerby.kerberos.kerb.type.ticket.EncTicketPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MemoryIdentityBackendTest {
    private final MemoryIdentityBackend backend = new MemoryIdentityBackend();

    @BeforeEach
    void startBackend(@TempDir Path directory) throws KrbException {
        BackendConfig config = new BackendConfig();
        config.setConfDir(directory.toFile());
        backend.setConfig(config);
        backend.initialize();
        backend.start();

        assertThat(backend.getConfig()).isSameAs(config);
        assertThat(config.getConfDir()).isEqualTo(directory.toFile());
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
    void createsListsUpdatesAndDeletesAccounts() throws KrbException {
        assertThat(backend.getIdentities()).isEmpty();
        assertThat(backend.getIdentity("missing@EXAMPLE.COM")).isNull();

        KrbIdentity bob = new KrbIdentity("bob@EXAMPLE.COM");
        KrbIdentity alice = new KrbIdentity("alice@EXAMPLE.COM");
        assertThat(backend.addIdentity(bob)).isSameAs(bob);
        assertThat(backend.addIdentity(alice)).isSameAs(alice);
        assertThat(backend.getIdentities()).containsExactly("alice@EXAMPLE.COM", "bob@EXAMPLE.COM");
        assertThat(backend.getIdentity(alice.getPrincipalName())).isSameAs(alice);

        KrbIdentity updated = new KrbIdentity(alice.getPrincipalName());
        updated.setDisabled(true);
        updated.setKeyVersion(4);
        backend.updateIdentity(updated);

        KrbIdentity stored = backend.getIdentity(alice.getPrincipalName());
        assertThat(stored.isDisabled()).isTrue();
        assertThat(stored.getKeyVersion()).isEqualTo(4);
        assertThat(backend.getIdentity(bob.getPrincipalName())).isSameAs(bob);

        backend.deleteIdentity(alice.getPrincipalName());
        assertThat(backend.getIdentity(alice.getPrincipalName())).isNull();
        assertThat(backend.getIdentities()).containsExactly("bob@EXAMPLE.COM");
        backend.deleteIdentity(bob.getPrincipalName());
        assertThat(backend.getIdentities()).isEmpty();
    }

    @Test
    void validatesAccountOperations() throws KrbException {
        KrbIdentity alice = new KrbIdentity("alice@EXAMPLE.COM");
        backend.addIdentity(alice);

        assertThatThrownBy(() -> backend.addIdentity(new KrbIdentity(alice.getPrincipalName())))
                .isInstanceOf(KrbException.class);
        assertThatThrownBy(() -> backend.updateIdentity(new KrbIdentity("missing@EXAMPLE.COM")))
                .isInstanceOf(KrbException.class);
        assertThatThrownBy(() -> backend.deleteIdentity("missing@EXAMPLE.COM"))
                .isInstanceOf(KrbException.class);
        assertThatThrownBy(() -> backend.getIdentity(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backend.getIdentity(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backend.addIdentity(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backend.updateIdentity(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backend.deleteIdentity(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(backend.getIdentities()).containsExactly(alice.getPrincipalName());
        assertThat(backend.getIdentity(alice.getPrincipalName())).isSameAs(alice);
    }

    @Test
    void supportsPasswordRequestsWithoutAdditionalAuthorizationData() throws KrbException {
        KdcClientRequest request = new KdcClientRequest();
        request.setClientPrincipal(new PrincipalName("alice@EXAMPLE.COM"));
        request.setToken(false);

        assertThat(backend.getIdentityAuthorizationData(request, new EncTicketPart())).isNull();
        assertThat(backend.supportBatchTrans()).isFalse();
        assertThatThrownBy(backend::startBatchTrans).isInstanceOf(KrbException.class);
    }
}
