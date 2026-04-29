/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.apache.commons.crypto.Crypto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class NativeCodeLoaderTest {
    @TempDir
    Path temporaryNativeLibraryDirectory;

    @Test
    void nativeCodeLoaderChecksPackagedNativeLibraryResourceWhenReportingLoadStatus() {
        final String originalTemporaryDirectory = System.getProperty(Crypto.LIB_TEMPDIR_KEY);
        try {
            System.setProperty(Crypto.LIB_TEMPDIR_KEY, temporaryNativeLibraryDirectory.toString());

            final boolean nativeCodeLoaded = Crypto.isNativeCodeLoaded();
            final Throwable loadingError = Crypto.getLoadingError();

            if (nativeCodeLoaded) {
                assertThat(loadingError).isNull();
            } else {
                assertThat(loadingError).isNotNull();
            }
        } finally {
            if (originalTemporaryDirectory == null) {
                System.clearProperty(Crypto.LIB_TEMPDIR_KEY);
            } else {
                System.setProperty(Crypto.LIB_TEMPDIR_KEY, originalTemporaryDirectory);
            }
        }
    }
}
