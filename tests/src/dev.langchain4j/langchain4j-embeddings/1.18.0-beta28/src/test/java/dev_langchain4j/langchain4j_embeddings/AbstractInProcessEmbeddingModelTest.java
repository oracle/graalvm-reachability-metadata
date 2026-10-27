/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_embeddings;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

public class AbstractInProcessEmbeddingModelTest {

    @Test
    void embedsTextWithTheBundledOnnxModel() {
        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel(Runnable::run);

        Response<Embedding> response = model.embed("A sentence for a bundled embedding model.");

        assertThat(response.content()).isNotNull();
        assertThat(response.content().dimension()).isEqualTo(384);
        assertThat(response.content().vector()).hasSize(384);
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.tokenUsage().inputTokenCount()).isPositive();
    }
}
