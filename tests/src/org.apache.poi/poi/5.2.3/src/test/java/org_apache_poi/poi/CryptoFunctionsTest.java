/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;
import java.util.HexFormat;

import org.apache.poi.poifs.crypt.CryptoFunctions;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.junit.jupiter.api.Test;

public class CryptoFunctionsTest {

    @Test
    void getMessageDigestRegistersBouncyCastleWhenNeeded() {
        Provider existingProvider = Security.getProvider("BC");
        if (existingProvider != null) {
            Security.removeProvider("BC");
        }

        try {
            assertThat(Security.getProvider("BC")).isNull();

            MessageDigest messageDigest = CryptoFunctions.getMessageDigest(HashAlgorithm.md4);
            String digest = HexFormat.of().formatHex(messageDigest.digest("Apache POI".getBytes(StandardCharsets.UTF_8)));

            assertThat(Security.getProvider("BC")).isNotNull();
            assertThat(messageDigest.getProvider().getName()).isEqualTo("BC");
            assertThat(messageDigest.getAlgorithm()).isEqualTo("MD4");
            assertThat(digest).isEqualTo("e8c294ed1470406b369771f6c56c96d5");
        } finally {
            if (existingProvider != null) {
                Security.removeProvider("BC");
                Security.addProvider(existingProvider);
            }
        }
    }
}
