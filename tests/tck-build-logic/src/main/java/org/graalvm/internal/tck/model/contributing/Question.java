/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
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
