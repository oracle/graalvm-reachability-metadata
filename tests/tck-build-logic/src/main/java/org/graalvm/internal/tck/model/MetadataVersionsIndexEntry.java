/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Index entry for metadata/<groupId>/<artifactId>/index.json.
 * The groupId and artifactId are derived from the directory path; no explicit "module" field exists.
 */
public record MetadataVersionsIndexEntry(
        Boolean latest,
        Boolean override,
        @JsonProperty("default-for")
        String defaultFor,
        @JsonProperty("metadata-version")
        String metadataVersion,
        @JsonProperty("test-version")
        String testVersion,
        @JsonProperty("tested-versions")
        List<String> testedVersions,
        @JsonProperty("skipped-versions")
        List<SkippedVersionEntry> skippedVersions,
        @JsonProperty("allowed-packages")
        List<String> allowedPackages,
        @JsonProperty("requires")
        List<String> requires
) {}
