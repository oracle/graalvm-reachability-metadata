/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bookkeeper.circe_checksum;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scurrilous.circe.utils.NativeUtils;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.Test;

public class NativeUtilsTest {

    @Test
    void loadLibraryFromJarLooksUpAbsoluteClasspathResource() {
        assertThatThrownBy(() -> NativeUtils.loadLibraryFromJar("/missing-circe-native-library.so"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("/missing-circe-native-library.so");
    }
}
