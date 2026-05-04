/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api.gax;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.gax.core.GaxProperties;
import org.junit.jupiter.api.Test;

public class GaxPropertiesTest {
    @Test
    void getLibraryVersionReadsDependencyPropertiesResource() {
        String libraryVersion = GaxProperties.getLibraryVersion(GaxProperties.class, "version.gax");

        assertThat(libraryVersion).isNotBlank();
        assertThat(libraryVersion).isEqualTo(GaxProperties.getGaxVersion());
    }

    @Test
    void getLibraryVersionReturnsDefaultForMissingProperty() {
        String libraryVersion = GaxProperties.getLibraryVersion(GaxProperties.class, "missing.gax.property");

        assertThat(libraryVersion).isEmpty();
    }
}
