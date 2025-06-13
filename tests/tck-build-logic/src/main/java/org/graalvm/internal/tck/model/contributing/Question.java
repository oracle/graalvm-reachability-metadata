package org.graalvm.internal.tck.model.contributing;

import com.fasterxml.jackson.annotation.JsonProperty;

/*
 * JSON model for metadata/index.json.
 */
public record Question(
        @JsonProperty("question-key")
        String questionKey,
        String question,

        String help
) {
}
