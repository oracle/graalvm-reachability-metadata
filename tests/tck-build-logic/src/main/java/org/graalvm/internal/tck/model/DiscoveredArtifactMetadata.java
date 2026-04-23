/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Build-local discovered metadata for a single library coordinate.
 */
public record DiscoveredArtifactMetadata(
        String coordinates,
        @JsonProperty("source-code-url")
        String sourceCodeUrl,
        @JsonProperty("repository-url")
        String repositoryUrl,
        @JsonProperty("test-code-url")
        String testCodeUrl,
        @JsonProperty("documentation-url")
        String documentationUrl,
        @JsonProperty("description")
        String description,
        @JsonProperty("language")
        LibraryLanguage language
) {}
