/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_crypto;

import org.apache.commons.crypto.Crypto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeCodeLoaderTest {
    @Test
    void initializesNativeLoaderThroughCryptoFacade() {
        boolean nativeCodeLoaded = Crypto.isNativeCodeLoaded();
        Throwable loadingError = Crypto.getLoadingError();

        if (nativeCodeLoaded) {
            assertThat(loadingError).isNull();
        } else {
            assertThat(loadingError).isNotNull();
        }
    }
}
