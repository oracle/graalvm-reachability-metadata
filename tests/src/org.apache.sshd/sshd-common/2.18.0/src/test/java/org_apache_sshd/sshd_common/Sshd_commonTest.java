/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.BuiltinIdentities;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.junit.jupiter.api.Test;

public class Sshd_commonTest {
    @Test
    void resolvesGeneratedKeyFromAuthorizedKeyEntry() throws IOException, GeneralSecurityException {
        KeyPair keyPair = KeyUtils.generateKeyPair(
                BuiltinIdentities.RSA.getSupportedKeyTypes().first(), 2048);
        String keyLine = PublicKeyEntry.toString(keyPair.getPublic()) + " generated-key";

        AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(keyLine);
        assertThat(entry.getComment()).isEqualTo("generated-key");
        assertThat(entry.resolvePublicKey(null, PublicKeyEntryResolver.FAILING))
                .satisfies(publicKey -> assertThat(KeyUtils.compareKeys(keyPair.getPublic(), publicKey)).isTrue());
    }
}
