/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.NativeLibrary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NativeLibraryTest {
    @Test
    void reportsSuppressedCausesWhenLibraryCannotBeLoaded() {
        String libraryName = "jna_suppressed_missing_library_probe_43b6fd2c";

        assertThatThrownBy(() -> NativeLibrary.getInstance(libraryName))
                .isInstanceOfSatisfying(UnsatisfiedLinkError.class, error -> {
                    assertThat(error)
                            .hasMessageContaining("Unable to load library")
                            .hasMessageContaining(libraryName);
                    assertThat(error.getSuppressed()).isNotEmpty();
                });
    }
}
