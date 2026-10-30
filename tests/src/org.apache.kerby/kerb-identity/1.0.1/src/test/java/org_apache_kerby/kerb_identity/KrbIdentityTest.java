/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_identity;

import org.apache.kerby.kerberos.kerb.identity.KrbIdentity;
import org.apache.kerby.kerberos.kerb.type.KerberosTime;
import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey;
import org.apache.kerby.kerberos.kerb.type.base.EncryptionType;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class KrbIdentityTest {
    @Test
    void maintainsPrincipalAndAccountSettings() {
        KrbIdentity identity = new KrbIdentity("alice@EXAMPLE.COM");

        assertThat(identity.getPrincipalName()).isEqualTo("alice@EXAMPLE.COM");
        assertThat(identity.getKeyVersion()).isEqualTo(1);
        assertThat(identity.getKdcFlags()).isZero();
        assertThat(identity.isDisabled()).isFalse();
        assertThat(identity.isLocked()).isFalse();
        assertThat(identity.getExpireTime()).isEqualTo(KerberosTime.NEVER);
        assertThat(identity.getCreatedTime()).isNotNull();
        assertThat(identity.getKeys()).isEmpty();

        KerberosTime created = new KerberosTime(1_700_000_000_000L);
        KerberosTime expires = new KerberosTime(1_800_000_000_000L);
        identity.setCreatedTime(created);
        identity.setExpireTime(expires);
        identity.setKeyVersion(7);
        identity.setKdcFlags(0x20);
        identity.setDisabled(true);
        identity.setLocked(true);
        identity.setPrincipalName("service/host@EXAMPLE.COM");

        assertThat(identity.getPrincipal().getName()).isEqualTo("service/host@EXAMPLE.COM");
        assertThat(identity.getCreatedTime()).isEqualTo(created);
        assertThat(identity.getExpireTime()).isEqualTo(expires);
        assertThat(identity.getKeyVersion()).isEqualTo(7);
        assertThat(identity.getKdcFlags()).isEqualTo(0x20);
        assertThat(identity.isDisabled()).isTrue();
        assertThat(identity.isLocked()).isTrue();

        PrincipalName renamed = new PrincipalName("bob@EXAMPLE.COM");
        identity.setPrincipal(renamed);
        assertThat(identity.getPrincipalName()).isEqualTo("bob@EXAMPLE.COM");
    }

    @Test
    void selectsAndReplacesKeysByEncryptionType() {
        KrbIdentity identity = new KrbIdentity("alice@EXAMPLE.COM");
        EncryptionKey aes128 = new EncryptionKey(EncryptionType.AES128_CTS_HMAC_SHA1_96, new byte[16], 2);
        EncryptionKey aes256 = new EncryptionKey(EncryptionType.AES256_CTS_HMAC_SHA1_96, new byte[32], 2);
        identity.addKeys(List.of(aes128, aes256));

        assertThat(identity.getKeys()).hasSize(2);
        assertThat(identity.getKey(EncryptionType.AES128_CTS_HMAC_SHA1_96)).isSameAs(aes128);
        assertThat(identity.getKey(EncryptionType.AES256_CTS_HMAC_SHA1_96)).isSameAs(aes256);

        byte[] rotatedKey = new byte[16];
        rotatedKey[0] = 42;
        EncryptionKey replacement = new EncryptionKey(EncryptionType.AES128_CTS_HMAC_SHA1_96, rotatedKey, 3);
        identity.addKey(replacement);

        assertThat(identity.getKeys()).hasSize(2);
        assertThat(identity.getKey(EncryptionType.AES128_CTS_HMAC_SHA1_96)).isSameAs(replacement);
        assertThat(identity.getKey(EncryptionType.AES128_CTS_HMAC_SHA1_96).getKeyData()).containsExactly(rotatedKey);
        assertThat(identity.getKey(EncryptionType.AES256_CTS_HMAC_SHA1_96)).isSameAs(aes256);
    }

    @Test
    void identifiesAccountsByPrincipal() {
        KrbIdentity first = new KrbIdentity("alice@EXAMPLE.COM");
        KrbIdentity samePrincipal = new KrbIdentity("alice@EXAMPLE.COM");
        samePrincipal.setKeyVersion(9);
        samePrincipal.setDisabled(true);

        assertThat(first).isEqualTo(samePrincipal);
        assertThat(first.hashCode()).isEqualTo(samePrincipal.hashCode());
        assertThat(first).isNotEqualTo(new KrbIdentity("bob@EXAMPLE.COM"));
    }
}
