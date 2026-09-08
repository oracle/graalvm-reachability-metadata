/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_embeddings_all_minilm_l6_v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModelFactory;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

public class Langchain4j_embeddings_all_minilm_l6_v2Test {

    private static final int EMBEDDING_DIMENSION = 384;

    @Test
    void embedsTextUsingTheBundledModelAndTokenizer() {
        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel(new RecordingExecutor());

        Response<Embedding> response = model.embed("I love sentence transformers.");
        float[] vector = response.content().vector();

        assertThat(vector).hasSize(EMBEDDING_DIMENSION);
        assertThat(vector[0]).isCloseTo(-0.0803190097f, withPercentage(1));
        assertThat(vector[1]).isCloseTo(-0.0171345081f, withPercentage(1));
        assertThat(vector[382]).isCloseTo(0.0478825271f, withPercentage(1));
        assertThat(vector[383]).isCloseTo(-0.0561899580f, withPercentage(1));
        assertThat(magnitude(vector)).isCloseTo(1.0, withPercentage(0.01));

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(tokenUsage.inputTokenCount());
        assertThat(response.finishReason()).isNull();
    }

    @Test
    void embedsMultipleSegmentsInOrderUsingTheProvidedExecutor() {
        RecordingExecutor executor = new RecordingExecutor();
        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel(executor);
        List<TextSegment> segments = List.of(
                TextSegment.from("hi"), TextSegment.from("hello"), TextSegment.from("hi"));

        Response<List<Embedding>> response = model.embedAll(segments);
        List<Embedding> embeddings = response.content();

        assertThat(executor.executionCount()).isEqualTo(segments.size());
        assertThat(embeddings).hasSize(segments.size());
        assertThat(embeddings.get(0)).isEqualTo(embeddings.get(2));
        assertThat(embeddings.get(0)).isNotEqualTo(embeddings.get(1));
        assertThat(embeddings).allSatisfy(embedding ->
                assertThat(embedding.dimension()).isEqualTo(EMBEDDING_DIMENSION));

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(3);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(3);
        assertThat(response.finishReason()).isNull();
    }

    @Test
    void embedsMultipartTextInputAsOneEmbedding() {
        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel(new RecordingExecutor());
        EmbeddingRequest multipartRequest = EmbeddingRequest.builder()
                .input(TextContent.from("sentence transformers"), TextContent.from("create useful embeddings"))
                .build();
        EmbeddingRequest joinedRequest = EmbeddingRequest.builder()
                .input("sentence transformers\ncreate useful embeddings")
                .build();

        EmbeddingResponse multipartResponse = model.embed(multipartRequest);
        EmbeddingResponse joinedResponse = model.embed(joinedRequest);

        assertThat(multipartResponse.embeddings()).singleElement().satisfies(embedding -> {
            assertThat(embedding.dimension()).isEqualTo(EMBEDDING_DIMENSION);
            assertThat(magnitude(embedding.vector())).isCloseTo(1.0, withPercentage(0.01));
        });
        assertThat(multipartResponse.embeddings()).isEqualTo(joinedResponse.embeddings());
        assertThat(multipartResponse.tokenUsage()).isNotNull();
        assertThat(multipartResponse.tokenUsage().inputTokenCount())
                .isEqualTo(joinedResponse.tokenUsage().inputTokenCount());
        assertThat(multipartResponse.modelName()).isEqualTo(model.modelName());
    }

    @Test
    void placesSemanticallyRelatedSentencesCloserTogether() {
        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel(new RecordingExecutor());
        List<TextSegment> segments = List.of(
                TextSegment.from("A puppy is playing with a ball in the garden."),
                TextSegment.from("A young dog chases a toy outdoors."),
                TextSegment.from("Quarterly financial reports describe corporate revenue."));

        List<Embedding> embeddings = model.embedAll(segments).content();

        double relatedSimilarity = cosineSimilarity(embeddings.get(0), embeddings.get(1));
        double unrelatedSimilarity = cosineSimilarity(embeddings.get(0), embeddings.get(2));
        assertThat(relatedSimilarity).isGreaterThan(unrelatedSimilarity);
    }

    @Test
    void embedsLongText() {
        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel(new RecordingExecutor());

        Response<Embedding> response = model.embed("hello ".repeat(511));

        assertThat(response.content().dimension()).isEqualTo(EMBEDDING_DIMENSION);
        assertThat(magnitude(response.content().vector())).isCloseTo(1.0, withPercentage(0.01));
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.tokenUsage().inputTokenCount()).isPositive();
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount());
    }

    @Test
    void exposesTheKnownDimensionAndServiceLoadedFactory() {
        EmbeddingModelFactory factory = loadFactory();

        EmbeddingModel model = factory.create();

        assertThat(factory).isInstanceOf(AllMiniLmL6V2EmbeddingModelFactory.class);
        assertThat(model).isInstanceOf(AllMiniLmL6V2EmbeddingModel.class);
        assertThat(model.dimension()).isEqualTo(EMBEDDING_DIMENSION);
    }

    private static EmbeddingModelFactory loadFactory() {
        for (EmbeddingModelFactory factory : ServiceLoader.load(EmbeddingModelFactory.class)) {
            if (factory instanceof AllMiniLmL6V2EmbeddingModelFactory) {
                return factory;
            }
        }
        throw new AssertionError("AllMiniLmL6V2 embedding model factory was not service-loadable");
    }

    private static double cosineSimilarity(Embedding left, Embedding right) {
        float[] leftVector = left.vector();
        float[] rightVector = right.vector();
        double dotProduct = 0.0;
        for (int i = 0; i < leftVector.length; i++) {
            dotProduct += leftVector[i] * rightVector[i];
        }
        return dotProduct / (magnitude(leftVector) * magnitude(rightVector));
    }

    private static double magnitude(float[] vector) {
        double sumOfSquares = 0.0;
        for (float value : vector) {
            sumOfSquares += value * value;
        }
        return Math.sqrt(sumOfSquares);
    }

    private static final class RecordingExecutor implements Executor {

        private int executionCount;

        @Override
        public void execute(Runnable command) {
            executionCount++;
            command.run();
        }

        int executionCount() {
            return executionCount;
        }
    }
}
