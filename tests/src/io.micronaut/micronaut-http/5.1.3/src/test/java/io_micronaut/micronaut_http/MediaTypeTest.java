/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_http;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.http.MediaType;
import org.junit.jupiter.api.Test;

public class MediaTypeTest {
    @Test
    void detectsMediaTypeFromFilenameUsingPackagedMimeTable() {
        MediaType mediaType = MediaType.forFilename("reports/summary.json");

        assertThat(mediaType.getName()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(mediaType.getExtension()).isEqualTo(MediaType.EXTENSION_JSON);
        assertThat(MediaType.forExtension(MediaType.EXTENSION_JSON)).contains(mediaType);
    }
}
