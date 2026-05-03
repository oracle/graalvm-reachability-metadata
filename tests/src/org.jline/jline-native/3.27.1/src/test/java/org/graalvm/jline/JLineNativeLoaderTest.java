/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import java.nio.file.Files;
import java.nio.file.Path;

import org.jline.nativ.JLineNativeLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JLineNativeLoaderTest {
    @Test
    void initializesNativeLoaderFromPackagedResource() {
        assertThat(JLineNativeLoader.initialize()).isTrue();

        Path nativeLibrary = Path.of(JLineNativeLoader.getNativeLibraryPath());
        assertThat(nativeLibrary).exists();
        assertThat(nativeLibrary.getFileName().toString())
                .startsWith("jlinenative-")
                .contains("jlinenative");
        assertThat(Files.isRegularFile(nativeLibrary)).isTrue();
        assertThat(Files.isReadable(nativeLibrary)).isTrue();

        assertThat(JLineNativeLoader.getNativeLibrarySourceUrl())
                .isNotBlank()
                .contains("org/jline/nativ/")
                .contains("jlinenative");
        assertThat(JLineNativeLoader.initialize()).isTrue();
    }
}
