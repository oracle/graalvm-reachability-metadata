/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bookkeeper.circe_checksum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.scurrilous.circe.utils.NativeUtils;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.Test;

public class NativeUtilsTest {
    @Test
    void attemptsToLoadNativeLibraryResourceFromClasspath() {
        String missingLibraryPath = "/circe-checksum-test/missing-native-library."
                + NativeUtils.libType();

        FileNotFoundException exception = assertThrows(
                FileNotFoundException.class,
                () -> NativeUtils.loadLibraryFromJar(missingLibraryPath));

        assertThat(exception).hasMessageContaining(missingLibraryPath);
    }
}
