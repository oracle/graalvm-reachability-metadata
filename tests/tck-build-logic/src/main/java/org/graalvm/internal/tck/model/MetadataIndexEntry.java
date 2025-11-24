/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/*
 * JSON model for metadata/index.json.
 */
public record MetadataIndexEntry(
        String directory,
        String module,
        List<String> requires,
        @JsonProperty("allowed-packages")
        List<String> allowedPackages
) {
}
