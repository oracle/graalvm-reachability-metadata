/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.nio.charset.StandardCharsets;

import org.codehaus.plexus.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Encodes and decodes binary application data with Plexus Base64 utilities.
 *
 * An artifact service stores a small binary manifest in a text-only metadata
 * field and restores the original bytes when a client downloads the artifact.
 */
class Base64EncodingTest {

    @Test
    void roundTripsBinaryManifestThroughTextMetadata() {
        byte[] manifest = "artifact=catalog\nchecksum=42\n".getBytes(StandardCharsets.UTF_8);

        byte[] encodedManifest = Base64.encodeBase64(manifest);
        byte[] restoredManifest = Base64.decodeBase64(encodedManifest);

        assertThat(new String(encodedManifest, StandardCharsets.US_ASCII))
                .isEqualTo("YXJ0aWZhY3Q9Y2F0YWxvZwpjaGVja3N1bT00Mgo=");
        assertThat(Base64.isArrayByteBase64(encodedManifest)).isTrue();
        assertThat(restoredManifest).containsExactly(manifest);
    }
}
