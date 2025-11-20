package org.graalvm.internal.tck.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MetadataVersionsIndexEntry (
        Boolean latest,
        Boolean override,
        String module,
        @JsonProperty("default-for")
        String defaultFor,
        @JsonProperty("metadata-version")
        String metadataVersion,
        @JsonProperty("tested-versions")
        List<String> testedVersions,
        @JsonProperty("skipped-versions")
        List<SkippedVersionEntry> skippedVersions
){}
