/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import java.security.GeneralSecurityException;
import java.security.Provider;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

final class BuildTimeBouncyCastleProvider {
    private static final Provider PROVIDER = createProvider();

    private BuildTimeBouncyCastleProvider() {
    }

    static Provider provider() {
        return PROVIDER;
    }

    private static Provider createProvider() {
        try {
            Provider provider = new BouncyCastleProvider();
            Cipher.getInstance("AES/GCM/NoPadding", provider);
            SecretKeyFactory.getInstance("AES", provider);
            return provider;
        } catch (GeneralSecurityException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
