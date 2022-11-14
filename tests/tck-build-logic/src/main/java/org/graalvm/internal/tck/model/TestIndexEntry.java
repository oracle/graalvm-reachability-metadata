package org.graalvm.internal.tck.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/*
 * JSON model for tests/src/index.json.
 */
public record TestIndexEntry(
        @JsonProperty("test-project-path") String testProjectPath,
        List<LibraryEntry> libraries
) {
    public record LibraryEntry(
            String name,
            List<String> versions
    ) {
    }
}
