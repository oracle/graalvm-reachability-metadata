/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Index entry for metadata/<groupId>/<artifactId>/index.json.
 * The groupId and artifactId are derived from the directory path; no explicit "module" field exists.
 * Automation fields follow §FS-library-version-update-automation.1 and §AR-test-harness.7.
 */
public record MetadataVersionsIndexEntry(
        Boolean latest,
        @JsonProperty("auto-update")
        Boolean autoUpdate,
        @JsonProperty("auto-update-lines")
        List<String> autoUpdateLines,
        @JsonProperty("high-priority")
        Boolean highPriority,
        Boolean override,
        @JsonProperty("default-for")
        String defaultFor,
        @JsonProperty("metadata-version")
        String metadataVersion,
        @JsonProperty("test-version")
        String testVersion,
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
        LibraryLanguage language,
        @JsonProperty("tested-versions")
        List<String> testedVersions,
        @JsonProperty("skipped-versions")
        List<SkippedVersionEntry> skippedVersions,
        @JsonProperty("allowed-packages")
        List<String> allowedPackages,
        @JsonProperty("requires")
        List<String> requires,
        @JsonProperty("not-for-native-image")
        Boolean notForNativeImage,
        @JsonProperty("reason")
        String reason,
        @JsonProperty("replacement")
        String replacement
) {
    public MetadataVersionsIndexEntry withTestedVersions(List<String> testedVersions) {
        return new MetadataVersionsIndexEntry(
                this.latest(),
                this.autoUpdate(),
                this.autoUpdateLines(),
                this.highPriority(),
                this.override(),
                this.defaultFor(),
                this.metadataVersion(),
                this.testVersion(),
                this.sourceCodeUrl(),
                this.repositoryUrl(),
                this.testCodeUrl(),
                this.documentationUrl(),
                this.description(),
                this.language(),
                testedVersions,
                this.skippedVersions(),
                this.allowedPackages(),
                this.requires(),
                this.notForNativeImage(),
                this.reason(),
                this.replacement()
        );
    }

    public MetadataVersionsIndexEntry withLatest(
            Boolean latest,
            Boolean autoUpdate,
            Boolean highPriority
    ) {
        return new MetadataVersionsIndexEntry(
                latest,
                autoUpdate,
                Boolean.TRUE.equals(latest) ? this.autoUpdateLines() : null,
                highPriority,
                this.override(),
                this.defaultFor(),
                this.metadataVersion(),
                this.testVersion(),
                this.sourceCodeUrl(),
                this.repositoryUrl(),
                this.testCodeUrl(),
                this.documentationUrl(),
                this.description(),
                this.language(),
                this.testedVersions(),
                this.skippedVersions(),
                this.allowedPackages(),
                this.requires(),
                this.notForNativeImage(),
                this.reason(),
                this.replacement()
        );
    }

    @JsonIgnore
    public boolean isNotForNativeImage() {
        return Boolean.TRUE.equals(notForNativeImage);
    }

    @JsonIgnore
    public boolean isSupportedMetadataEntry() {
        return !isNotForNativeImage() && metadataVersion != null && testedVersions != null;
    }
}
