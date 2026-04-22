/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.poifs.crypt.CryptoFunctions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.security.Security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CryptoFunctionsDynamicAccessTest {

    @AfterEach
    void removeBouncyCastleProvider() {
        Security.removeProvider("BC");
    }

    @Test
    void registersBouncyCastleWhenAvailableOrReportsThatItIsMissing() {
        boolean providerClassPresent = CryptoFunctionsDynamicAccessTest.class.getClassLoader()
                .getResource("org/bouncycastle/jce/provider/BouncyCastleProvider.class") != null;

        Security.removeProvider("BC");

        if (providerClassPresent) {
            CryptoFunctions.registerBouncyCastle();
            assertThat(Security.getProvider("BC")).isNotNull();
        } else {
            assertThatThrownBy(CryptoFunctions::registerBouncyCastle)
                    .isInstanceOf(EncryptedDocumentException.class)
                    .hasMessageContaining("BouncyCastle");
        }
    }
}
