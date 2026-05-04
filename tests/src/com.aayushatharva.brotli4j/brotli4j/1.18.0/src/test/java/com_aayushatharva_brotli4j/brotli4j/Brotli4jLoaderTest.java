/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_aayushatharva_brotli4j.brotli4j;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Brotli4jLoaderTest {
    @Test
    void loaderReportsNativeLibraryAvailability() {
        boolean available = Brotli4jLoader.isAvailable();
        Throwable unavailabilityCause = Brotli4jLoader.getUnavailabilityCause();

        if (available) {
            assertThat(unavailabilityCause).isNull();
        } else {
            assertThat(unavailabilityCause).isNotNull();
            assertThatThrownBy(Brotli4jLoader::ensureAvailability)
                    .isInstanceOf(UnsatisfiedLinkError.class)
                    .hasCause(unavailabilityCause);
        }
    }
}
