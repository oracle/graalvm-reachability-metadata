/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.plexus.util.io.CachingWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publishes generated text artifacts with Plexus content-aware caching streams.
 *
 * A release publisher rewrites a manifest only when its generated contents differ,
 * avoiding unnecessary changes to an already published artifact.
 */
class CachingArtifactWriterTest {

    @Test
    void rewritesManifestOnlyWhenContentsChange(@TempDir Path workspace) throws Exception {
        Path manifest = workspace.resolve("release-manifest.txt");
        String initialManifest = "name=plexus-utils\nversion=4.0.3\n";
        String revisedManifest = "name=plexus-utils\nversion=4.0.3\nchannel=stable\n";

        CachingWriter firstWriter = new CachingWriter(manifest, StandardCharsets.UTF_8);
        try (firstWriter) {
            firstWriter.write(initialManifest);
        }
        assertThat(firstWriter.isModified()).isTrue();
        assertThat(Files.readString(manifest)).isEqualTo(initialManifest);

        CachingWriter unchangedWriter = new CachingWriter(manifest, StandardCharsets.UTF_8);
        try (unchangedWriter) {
            unchangedWriter.write(initialManifest);
        }
        assertThat(unchangedWriter.isModified()).isFalse();
        assertThat(Files.readString(manifest)).isEqualTo(initialManifest);

        CachingWriter revisedWriter = new CachingWriter(manifest, StandardCharsets.UTF_8);
        try (revisedWriter) {
            revisedWriter.write(revisedManifest);
        }
        assertThat(revisedWriter.isModified()).isTrue();
        assertThat(Files.readString(manifest)).isEqualTo(revisedManifest);
    }
}
