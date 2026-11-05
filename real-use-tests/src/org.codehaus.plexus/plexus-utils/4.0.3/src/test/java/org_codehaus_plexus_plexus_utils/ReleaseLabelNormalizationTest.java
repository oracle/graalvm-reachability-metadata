/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.util.Map;

import org.codehaus.plexus.util.StringUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Normalizes operator-provided release labels with Plexus string utilities.
 *
 * A deployment service accepts a label copied from an operator's terminal,
 * turns it into a stable identifier, and writes the identifier into a manifest.
 */
class ReleaseLabelNormalizationTest {

    @Test
    void writesManifestWithNormalizedReleaseIdentifier() {
        String operatorLabel = "  Catalog   Service\r\n  EU-West  ";
        String[] words = StringUtils.split(operatorLabel);
        for (int index = 0; index < words.length; index++) {
            words[index] = StringUtils.lowerCase(words[index]);
        }
        String releaseIdentifier = StringUtils.join(words, "-");

        String manifest = StringUtils.interpolate(
                "release=${release}\nchannel=${channel}\n",
                Map.of("release", releaseIdentifier, "channel", "stable"));

        assertThat(releaseIdentifier).isEqualTo("catalog-service-eu-west");
        assertThat(manifest).isEqualTo(
                "release=catalog-service-eu-west\nchannel=stable\n");
    }
}
