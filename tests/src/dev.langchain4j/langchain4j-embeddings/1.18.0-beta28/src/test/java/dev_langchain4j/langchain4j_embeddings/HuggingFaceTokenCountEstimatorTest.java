/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_embeddings;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenCountEstimator;
import org.junit.jupiter.api.Test;

public class HuggingFaceTokenCountEstimatorTest {

    @Test
    void countsTokensWithTheBundledTokenizer() {
        HuggingFaceTokenCountEstimator estimator = new HuggingFaceTokenCountEstimator();

        int tokenCount = estimator.estimateTokenCountInText("A sentence for token counting.");

        assertThat(tokenCount).isPositive();
    }
}
